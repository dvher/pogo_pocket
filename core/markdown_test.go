package pogocore

import (
	"reflect"
	"strings"
	"testing"
)

// Same cases as the desktop's frontend/src/lib/markdown.test.ts.
const groceries = "# Groceries\n\n- [ ] milk\n- [x] eggs\n  - [ ] nested\n1. [ ] numbered\n- plain item"

func TestToggleTask(t *testing.T) {
	for line, want := range map[int]string{2: "- [x] milk", 3: "- [ ] eggs", 4: "  - [x] nested", 5: "1. [x] numbered"} {
		if got := ToggleTask(groceries, line); !strings.Contains(got, want) {
			t.Errorf("line %d: %q", line, got)
		}
	}
	for _, line := range []int{0, 6, -1, 99} {
		if got := ToggleTask(groceries, line); got != groceries {
			t.Errorf("line %d changed a non-task: %q", line, got)
		}
	}
	if got := ToggleTask("> - [ ] quoted", 0); got != "> - [x] quoted" {
		t.Errorf("quoted task: %q", got)
	}
}

func TestLines(t *testing.T) {
	got := Lines(groceries)
	want := []Line{
		{Kind: "h1", Text: "Groceries", Line: 0},
		{Kind: "gap"},
		{Kind: "task", Text: "milk", Line: 2},
		{Kind: "task", Text: "eggs", Checked: true, Line: 3},
		{Kind: "task", Text: "nested", Indent: 1, Line: 4},
		{Kind: "task", Text: "numbered", Line: 5},
		{Kind: "bullet", Text: "plain item", Marker: "•", Line: 6},
	}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("got  %+v\nwant %+v", got, want)
	}
}

func TestLinesMixed(t *testing.T) {
	src := "## Plan\n\nSome **bold** and _it_ and [a link](https://x.y) in snake_case_name.\n" +
		"- see [ ] later\n- [ ]\n> quote\n\n---\n```\n# not a heading\n```\n\n\n"
	got := Lines(src)
	kinds := []string{}
	for _, l := range got {
		kinds = append(kinds, l.Kind)
	}
	if strings.Join(kinds, ",") != "h2,gap,text,bullet,task,quote,gap,rule,code" {
		t.Fatalf("kinds %v", kinds)
	}
	if got[2].Text != "Some bold and it and a link in snake_case_name." {
		t.Errorf("inline: %q", got[2].Text)
	}
	if got[3].Text != "see [ ] later" {
		t.Errorf("mid-text brackets: %q", got[3].Text)
	}
	if got[8].Text != "# not a heading" || got[8].Line != 9 {
		t.Errorf("code: %+v", got[8])
	}
	if len(Lines("")) != 0 {
		t.Error("empty note has lines")
	}
}

func TestTitleOf(t *testing.T) {
	for src, want := range map[string]string{"\n# Hello\nworld": "Hello", "- [ ] buy milk": "buy milk", "": "Empty note", "**Bold** start": "Bold start"} {
		if got := TitleOf(src); got != want {
			t.Errorf("TitleOf(%q) = %q, want %q", src, got, want)
		}
	}
}
