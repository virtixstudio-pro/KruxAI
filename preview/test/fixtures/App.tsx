import { useEffect, useState } from "react";

type Note = { id: number; text: string };
const KEY = "notes";

function load(): Note[] {
  try { return JSON.parse(localStorage.getItem(KEY) || "[]"); } catch { return []; }
}

export default function App() {
  const [notes, setNotes] = useState<Note[]>(load);
  const [text, setText] = useState("");
  useEffect(() => { localStorage.setItem(KEY, JSON.stringify(notes)); }, [notes]);

  const add = () => {
    const t = text.trim();
    if (!t) return;
    setNotes([{ id: Date.now(), text: t }, ...notes]);
    setText("");
    window.Krux?.vibrate(20);
  };

  return (
    <main>
      <h1>Notes</h1>
      <div className="row">
        <input value={text} onChange={(e) => setText(e.target.value)} placeholder="Écrire une note…" />
        <button onClick={add}>Ajouter</button>
      </div>
      {notes.length === 0 && <p className="empty">Aucune note pour l’instant.</p>}
      <ul>
        {notes.map((n) => (
          <li key={n.id}>
            <span>{n.text}</span>
            <button className="ghost" onClick={() => window.Krux?.share(n.text)}>Partager</button>
            <button className="ghost" onClick={() => setNotes(notes.filter((x) => x.id !== n.id))}>Supprimer</button>
          </li>
        ))}
      </ul>
    </main>
  );
}
