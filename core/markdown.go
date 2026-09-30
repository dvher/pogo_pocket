package pogocore

import (
	"regexp"
	"strings"
)

// Mirrors toggleTask in the desktop's frontend/src/lib/markdown.ts.
var taskLine = regexp.MustCompile(`^(\s*(?:>\s*)*(?:[-*+]|\d+[.)])\s+\[)([ xX])(\])`)

// ToggleTask flips the checkbox on the given 0-based source line.
func ToggleTask(src string, line int) string {
	lines := strings.Split(src, "\n")
	if line < 0 || line >= len(lines) {
		return src
	}
	m := taskLine.FindStringSubmatchIndex(lines[line])
	if m == nil {
		return src
	}
	mark := "x"
	if lines[line][m[4]:m[5]] != " " {
		mark = " "
	}
	lines[line] = lines[line][:m[4]] + mark + lines[line][m[5]:]
	return strings.Join(lines, "\n")
}

var titlePrefix = regexp.MustCompile(`^\s*(#+\s*|(?:[-*+]|\d+[.)])\s+)(\[[ xX]\]\s*)?`)

// TitleOf returns the first meaningful line, for lists and pickers.
func TitleOf(src string) string {
	for _, l := range strings.Split(src, "\n") {
		if t := stripInline(strings.TrimSpace(titlePrefix.ReplaceAllString(l, ""))); t != "" {
			return t
		}
	}
	return "Empty note"
}

// Line is one rendered line of a note, simple enough for a home-screen
// widget to draw with plain text views.
type Line struct {
	Kind    string `json:"kind"` // h1, h2, text, bullet, task, quote, code, rule, gap
	Text    string `json:"text"`
	Marker  string `json:"marker,omitempty"` // "•" or "1." for bullets
	Indent  int    `json:"indent"`           // nesting level
	Checked bool   `json:"checked"`          // tasks
	Line    int    `json:"line"`             // 0-based source line, for ToggleTask
}

var (
	heading   = regexp.MustCompile(`^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$`)
	listItem  = regexp.MustCompile(`^(\s*)([-*+]|\d+[.)])\s+(.*)$`)
	taskBody  = regexp.MustCompile(`^\[([ xX])\](?:[ \t]+(.*)|$)`)
	quoteLine = regexp.MustCompile(`^\s*>\s?(.*)$`)
	ruleLine  = regexp.MustCompile(`^\s{0,3}([-*_])(\s*[-*_]){2,}\s*$`)
	fence     = regexp.MustCompile("^\\s{0,3}(```|~~~)")
)

// Lines turns Markdown into widget lines. It covers what notes typically
// hold (headings, lists, tasks, quotes, code, rules) and strips inline
// formatting; the app's editor shows the full note.
func Lines(src string) []Line {
	var out []Line
	inCode := false
	gap := func() {
		if len(out) > 0 && out[len(out)-1].Kind != "gap" {
			out = append(out, Line{Kind: "gap"})
		}
	}
	for i, raw := range strings.Split(strings.ReplaceAll(src, "\r\n", "\n"), "\n") {
		if fence.MatchString(raw) {
			inCode = !inCode
			continue
		}
		if inCode {
			out = append(out, Line{Kind: "code", Text: raw, Line: i})
			continue
		}
		if strings.TrimSpace(raw) == "" {
			gap()
			continue
		}
		if ruleLine.MatchString(raw) {
			out = append(out, Line{Kind: "rule", Line: i})
			continue
		}
		if m := heading.FindStringSubmatch(raw); m != nil {
			kind := "h2"
			if len(m[1]) == 1 {
				kind = "h1"
			}
			out = append(out, Line{Kind: kind, Text: stripInline(m[2]), Line: i})
			continue
		}
		if m := listItem.FindStringSubmatch(raw); m != nil {
			indent := indentLevel(m[1])
			if t := taskBody.FindStringSubmatch(m[3]); t != nil {
				out = append(out, Line{Kind: "task", Text: stripInline(t[2]), Checked: t[1] != " ", Indent: indent, Line: i})
				continue
			}
			marker := "•"
			if m[2][0] >= '0' && m[2][0] <= '9' {
				marker = m[2]
			}
			out = append(out, Line{Kind: "bullet", Text: stripInline(m[3]), Marker: marker, Indent: indent, Line: i})
			continue
		}
		if m := quoteLine.FindStringSubmatch(raw); m != nil {
			out = append(out, Line{Kind: "quote", Text: stripInline(m[1]), Line: i})
			continue
		}
		out = append(out, Line{Kind: "text", Text: stripInline(strings.TrimSpace(raw)), Line: i})
	}
	for len(out) > 0 && out[len(out)-1].Kind == "gap" {
		out = out[:len(out)-1]
	}
	if out == nil {
		out = []Line{}
	}
	return out
}

func indentLevel(ws string) int {
	n := 0
	for _, r := range ws {
		if r == '\t' {
			n += 4
		} else {
			n++
		}
	}
	return n / 2
}

var inlineRules = []struct {
	re   *regexp.Regexp
	repl string
}{
	{regexp.MustCompile(`!\[([^\]]*)\]\([^)]*\)`), "$1"},  // images
	{regexp.MustCompile(`\[([^\]]+)\]\([^)]*\)`), "$1"},   // links
	{regexp.MustCompile("`([^`]+)`"), "$1"},               // code
	{regexp.MustCompile(`\*\*([^*]+)\*\*`), "$1"},         // bold
	{regexp.MustCompile(`__([^_]+)__`), "$1"},             // bold
	{regexp.MustCompile(`~~([^~]+)~~`), "$1"},             // strikethrough
	{regexp.MustCompile(`\*([^*\s][^*]*)\*`), "$1"},       // italic
	{regexp.MustCompile(`(^|\W)_([^_\s][^_]*)_(\W|$)`), "$1$2$3"}, // italic, not snake_case
	{regexp.MustCompile(`\\([\\*_\[\]()#+\-.!` + "`" + `~>])`), "$1"}, // escapes
}

func stripInline(s string) string {
	for _, r := range inlineRules {
		s = r.re.ReplaceAllString(s, r.repl)
	}
	return s
}

// LinesJSON returns Lines(src) as JSON, for the app's preview.
func LinesJSON(src string) string { return toJSON(Lines(src)) }
