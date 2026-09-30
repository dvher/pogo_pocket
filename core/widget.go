package pogocore

import (
	"github.com/dvher/pogo/pkg/palette"
	"github.com/dvher/pogo/pkg/store"
)

// Each widget on the home screen (identified by a host-chosen key, such as
// Android's appWidgetId) remembers the note it shows and that note's position,
// so it can move on to a neighbour when the note is deleted.
func widgetNoteKey(key string) string  { return "widget:" + key + ":note" }
func widgetIndexKey(key string) string { return "widget:" + key + ":index" }

// WidgetView is everything a widget needs to draw itself.
type WidgetView struct {
	State  string `json:"state"` // "note", "empty" or "locked"
	Note   *Note  `json:"note,omitempty"`
	BG     string `json:"bg"`
	Ink    string `json:"ink"`
	Accent string `json:"accent"`
	Lines  []Line `json:"lines"`
	Index  int    `json:"index"` // position of Note among all notes
	Count  int    `json:"count"`
}

// resolve returns the notes and the index of the widget's note in them,
// or -1 if there are none.
func (c *Core) resolve(key string) ([]store.Note, int, error) {
	notes, err := c.store.List()
	if err != nil || len(notes) == 0 {
		return notes, -1, err
	}
	id := c.store.Setting(widgetNoteKey(key), "")
	for i, n := range notes {
		if n.ID == id {
			return notes, i, nil
		}
	}
	// Unset, or the note was deleted: stay near where it was.
	i := int(c.store.IntSetting(widgetIndexKey(key), 0))
	return notes, min(max(i, 0), len(notes)-1), nil
}

func (c *Core) widgetShow(key string, notes []store.Note, i int) error {
	if err := c.store.SetSetting(widgetNoteKey(key), notes[i].ID); err != nil {
		return err
	}
	return c.store.SetIntSetting(widgetIndexKey(key), int64(i))
}

// WidgetSetNote makes a widget show the note with id.
func (c *Core) WidgetSetNote(key, id string) error {
	notes, _, err := c.resolve(key)
	if err != nil {
		return err
	}
	for i, n := range notes {
		if n.ID == id {
			return c.widgetShow(key, notes, i)
		}
	}
	return store.ErrNotFound
}

// WidgetNext moves a widget to the next note, wrapping around.
func (c *Core) WidgetNext(key string) error {
	notes, i, err := c.resolve(key)
	if err != nil || i < 0 {
		return err
	}
	return c.widgetShow(key, notes, (i+1)%len(notes))
}

// WidgetRemove forgets a widget's state after it is removed from the home screen.
func (c *Core) WidgetRemove(key string) {
	c.store.SetSetting(widgetNoteKey(key), "")
	c.store.SetSetting(widgetIndexKey(key), "")
}

// WidgetViewJSON returns the WidgetView for a widget as JSON.
func (c *Core) WidgetViewJSON(key string) (string, error) {
	v, err := c.widgetView(key)
	if err != nil {
		return "", err
	}
	return toJSON(v), nil
}

func (c *Core) widgetView(key string) (WidgetView, error) {
	notes, i, err := c.resolve(key)
	if err != nil {
		return WidgetView{}, err
	}
	col := palette.Get(palette.Default)
	v := WidgetView{Lines: []Line{}, Count: len(notes), Index: i}
	if i < 0 {
		v.State, v.Index = "empty", 0
		if c.engine.Status().E2ELocked {
			v.State = "locked"
		}
	} else {
		n := fromStore(notes[i])
		col = palette.Get(n.Color)
		v.State, v.Note, v.Lines = "note", &n, Lines(n.Content)
	}
	v.BG, v.Ink, v.Accent = hexRGB(col.BG), hexRGB(col.Ink), hexRGB(col.Accent)
	return v, nil
}

