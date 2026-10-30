package pogocore

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestExportImport(t *testing.T) {
	a := open(t)
	id, err := a.Create("# Groceries\n\n- [ ] milk\n")
	if err != nil {
		t.Fatal(err)
	}
	a.SetColor(id, "green")

	if name, _ := a.NoteFilename(id, ".md"); name != "Groceries.md" {
		t.Errorf("filename: %q", name)
	}
	md, _ := a.NoteMarkdown(id)
	if !strings.HasPrefix(string(md), "---\nid: "+id+"\ncolor: \"green\"\n") {
		t.Errorf("markdown: %s", md)
	}
	if html, _ := a.NoteHTML(id); !strings.Contains(html, "<title>Groceries</title>") || !strings.Contains(html, `type="checkbox"`) {
		t.Errorf("html: %s", html)
	}

	zip, err := a.ExportZip()
	if err != nil {
		t.Fatal(err)
	}
	b := open(t)
	rec := &recorder{}
	b.SetListener(rec)
	report, err := b.ImportFile(ExportZipName(), zip)
	if err != nil || report != `{"created":1,"updated":0,"skipped":0}` || rec.notes == 0 {
		t.Fatalf("import: %s %v (notified %d)", report, err, rec.notes)
	}
	var got Note
	js, _ := b.NoteJSON(id)
	json.Unmarshal([]byte(js), &got)
	if got.Color != "green" || got.Title != "Groceries" {
		t.Errorf("imported: %+v", got)
	}

	// A loose Markdown file, such as one from Obsidian, becomes a new note.
	if report, _ := b.ImportFile("idea.md", []byte("an idea\n")); report != `{"created":1,"updated":0,"skipped":0}` {
		t.Errorf("markdown import: %s", report)
	}
	if _, err := b.ImportFile("broken.zip", []byte("not a zip")); err == nil {
		t.Error("broken zip accepted")
	}
}
