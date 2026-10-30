package pogocore

import (
	"bytes"
	"time"

	"github.com/dvher/pogo/pkg/notefile"
	"github.com/dvher/pogo/pkg/store"
)

// liveNote returns a note that is not deleted.
func (c *Core) liveNote(id string) (store.Note, error) {
	n, err := c.store.Get(id)
	if err == nil && n.Deleted {
		err = store.ErrNotFound
	}
	return n, err
}

// NoteFilename suggests a file name for a note, with ext such as ".md".
func (c *Core) NoteFilename(id, ext string) (string, error) {
	n, err := c.liveNote(id)
	if err != nil {
		return "", err
	}
	return notefile.Filename(notefile.Title(n.Content), ext, nil), nil
}

// NoteMarkdown returns a note as a Markdown file with Pogo's frontmatter.
func (c *Core) NoteMarkdown(id string) ([]byte, error) {
	n, err := c.liveNote(id)
	if err != nil {
		return nil, err
	}
	return notefile.Markdown(n), nil
}

// NoteHTML returns a note as a standalone web page, for saving or printing.
func (c *Core) NoteHTML(id string) (string, error) {
	n, err := c.liveNote(id)
	if err != nil {
		return "", err
	}
	html, err := notefile.HTML(n)
	return string(html), err
}

// ExportZip returns every note as Markdown files in a zip archive, the same
// format as Pogo's desktop export.
func (c *Core) ExportZip() ([]byte, error) {
	notes, err := c.store.List()
	if err != nil {
		return nil, err
	}
	var buf bytes.Buffer
	err = notefile.WriteZip(&buf, notes)
	return buf.Bytes(), err
}

// ExportZipName suggests a file name for ExportZip.
func ExportZipName() string { return "pogo-notes-" + time.Now().Format("2006-01-02") + ".zip" }

// ImportFile imports a zip archive or a Markdown file, chosen by name's
// extension. It returns a report as JSON: {"created","updated","skipped"}.
func (c *Core) ImportFile(name string, data []byte) (string, error) {
	items, err := notefile.Parse(name, data, time.Time{})
	if err != nil {
		return "", err
	}
	r, err := notefile.Import(c.store, c.engine.DeviceID(), items)
	if r.Created+r.Updated > 0 {
		c.edited()
	}
	if err != nil {
		return "", err
	}
	return toJSON(r), nil
}
