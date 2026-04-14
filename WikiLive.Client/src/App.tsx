import { useState, useEffect } from 'react';
import { FileText, Plus, BookOpen, Clock, MessageSquare, ThumbsUp, CheckCircle, RotateCcw } from 'lucide-react';
import { pagesApi, commentsApi } from './api/api';
import './index.css';

interface Comment {
  id: string;
  text: string;
  selectedText?: string;
  resolved: boolean;
  likes: number;
  createdAt: string;
}

interface PageVersion {
  id: string;
  versionNumber: number;
  createdAt: string;
}

interface Page {
  id: string;
  title: string;
  comments?: Comment[];
  versions?: PageVersion[];
}

function App() {
  const [pages, setPages] = useState<Page[]>([]);
  const [selectedPage, setSelectedPage] = useState<Page | null>(null);
  const [sidebarTab, setSidebarTab] = useState<'comments' | 'history'>('comments');
  const [showRightSidebar, setShowRightSidebar] = useState(true);
  const [newComment, setNewComment] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => { loadPages(); }, []);
  useEffect(() => { if (selectedPage?.id) loadPageDetails(selectedPage.id); }, [selectedPage?.id]);

  const loadPages = async () => {
    try {
      const data = await pagesApi.getAll();
      setPages(data);
      if (data.length > 0 && !selectedPage) setSelectedPage(data[0]);
    } catch (e) { console.error(e); } finally { setLoading(false); }
  };

  const loadPageDetails = async (id: string) => {
    try {
      const data = await pagesApi.getById(id);
      setSelectedPage(data);
    } catch (e) { console.error(e); }
  };

  const handleRestore = async (versionId: string) => {
    if (!selectedPage) return;
    if (confirm('Are you sure you want to restore this version?')) {
      try {
        const updated = await pagesApi.restoreVersion(selectedPage.id, versionId);
        setSelectedPage(updated);
        alert('Version restored!');
      } catch (e) { alert('Error restoring version'); }
    }
  };

  const handleAddComment = async () => {
    if (!newComment.trim() || !selectedPage) return;
    try {
      const comment = await commentsApi.create({ pageId: selectedPage.id, text: newComment });
      setSelectedPage({ ...selectedPage, comments: [comment, ...(selectedPage.comments || [])] });
      setNewComment('');
    } catch (e) { alert('Error adding comment'); }
  };

  return (
    <>
      <aside className="sidebar">
        <h2><BookOpen size={24} color="#2563eb" /> WikiLive</h2>
        <ul className="page-list">
          {loading ? <p>Loading...</p> : pages.map((page) => (
            <li 
              key={page.id} 
              className={`page-item ${selectedPage?.id === page.id ? 'active' : ''}`}
              onClick={() => setSelectedPage(page)}
            >
              <FileText size={18} /> {page.title}
            </li>
          ))}
        </ul>
        <button className="add-button" onClick={() => {
          const title = prompt('Enter title:');
          if (title) pagesApi.create(title).then(loadPages);
        }}><Plus size={18} /> New Page</button>
      </aside>

      <main className="main-content">
        <header className="top-bar">
          <div className="status">{selectedPage ? `Editing: ${selectedPage.title}` : 'Select a page'}</div>
          <div className="actions" style={{ display: 'flex', gap: '1rem' }}>
             <Clock 
               size={20} 
               style={{ cursor: 'pointer', color: sidebarTab === 'history' && showRightSidebar ? '#2563eb' : '#64748b' }} 
               onClick={() => { setSidebarTab('history'); setShowRightSidebar(true); }}
             />
             <MessageSquare 
               size={20} 
               style={{ cursor: 'pointer', color: sidebarTab === 'comments' && showRightSidebar ? '#2563eb' : '#64748b' }} 
               onClick={() => { setSidebarTab('comments'); setShowRightSidebar(true); }}
             />
          </div>
        </header>

        <section style={{ display: 'flex', flexGrow: 1, overflow: 'hidden' }}>
          <div className="editor-container">
            {selectedPage ? (
              <div className="editor-placeholder">
                <h1>{selectedPage.title}</h1>
                <p style={{ marginTop: '1rem', color: '#64748b' }}>[Editor Content Placeholder]</p>
              </div>
            ) : <div style={{ textAlign: 'center', marginTop: '4rem' }}><p>Select a page</p></div>}
          </div>

          {showRightSidebar && selectedPage && (
            <aside className="comments-sidebar">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #e2e8f0', paddingBottom: '0.5rem' }}>
                 <h3 style={{ fontSize: '0.9rem', color: '#64748b' }}>{sidebarTab === 'comments' ? 'Comments' : 'History'}</h3>
                 <button onClick={() => setShowRightSidebar(false)} style={{ border: 'none', background: 'none', cursor: 'pointer' }}>×</button>
              </div>

              {sidebarTab === 'comments' ? (
                <>
                  <div className="comments-list" style={{ flexGrow: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '0.75rem', marginTop: '1rem' }}>
                    {selectedPage.comments?.map(c => (
                      <div key={c.id} className={`comment-card ${c.resolved ? 'resolved' : ''}`}>
                        <div className="comment-text">{c.text}</div>
                        <div className="comment-meta">
                           <span><ThumbsUp size={12}/> {c.likes}</span>
                           <span>{new Date(c.createdAt).toLocaleDateString()}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                  <div className="comment-input-area">
                    <textarea className="comment-input" value={newComment} onChange={(e) => setNewComment(e.target.value)} placeholder="Add comment..." />
                    <button className="add-button" style={{ width: '100%', justifyContent: 'center' }} onClick={handleAddComment}>Post</button>
                  </div>
                </>
              ) : (
                <div className="history-list" style={{ marginTop: '1rem' }}>
                  {selectedPage.versions?.map(v => (
                    <div key={v.id} className="version-card">
                      <div className="version-info">
                        <span className="version-number">Version #{v.versionNumber}</span>
                        <span className="version-date">{new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(v.createdAt))}</span>
                      </div>
                      <button className="restore-btn" onClick={() => handleRestore(v.id)}>
                        <RotateCcw size={14} /> Restore
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </aside>
          )}
        </section>
      </main>
    </>
  );
}

export default App;
