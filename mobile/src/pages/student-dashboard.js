/**
 * Tableau de bord Étudiant – UniAbsence
 * Futur : toutes les données viennent de GET /api/v1/students/me/absences (Spring Boot)
 */
import { AppState } from '../router.js';
import { clearAccessToken } from '../api/client.js';
import {
  downloadStudentJustification,
  fetchStudentDashboardData,
  submitStudentJustification,
} from '../api/student.js';

// ── Helpers ────────────────────────────────────────────────────────────────

function formatDate(iso) {
  if (!iso) return 'Date non renseignée';
  return new Date(iso).toLocaleDateString('fr-FR', {
    day: '2-digit', month: 'short', year: 'numeric',
  });
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  })[character]);
}

function statusLabel(s) {
  return s === 'justified' ? 'Justifiée' : 'Non justifiée';
}
function statusClass(s) {
  return s === 'justified' ? 'badge-ok' : 'badge-err';
}

function docStatusBadge(status) {
  switch (status) {
    case 'accepted':
      return `<span class="badge badge-doc-accepted">🟢 Accepté</span>`;
    case 'rejected':
      return `<span class="badge badge-doc-rejected">🔴 Refusé</span>`;
    case 'pending':
    default:
      return `<span class="badge badge-doc-pending">🟡 En attente</span>`;
  }
}

function hasStoredFile(filePath) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(pdf|jpg|png)$/i
    .test(String(filePath || ''));
}

// ── Barre de navigation ────────────────────────────────────────────────────

function renderNavBar(activeTab) {
  const tabs = [
    { id: 'home',      icon: iconHome(),     label: 'Accueil' },
    { id: 'absences',  icon: iconCalendar(), label: 'Absences' },
    { id: 'docs',      icon: iconDoc(),      label: 'Justificatifs' },
    { id: 'profile',   icon: iconUser(),     label: 'Profil' },
  ];

  return `
    <nav class="bottom-nav" role="navigation" aria-label="Navigation principale">
      ${tabs.map(t => `
        <button class="nav-item ${t.id === activeTab ? 'nav-active' : ''}"
                data-tab="${t.id}" aria-label="${t.label}">
          ${t.icon}
          <span class="nav-label">${t.label}</span>
        </button>
      `).join('')}
    </nav>
  `;
}

// ── Contenu des onglets ────────────────────────────────────────────────────

function renderHomeTab(student, stats, absences) {
  const recent = absences.slice(0, 3);
  const rate = stats.absenceRate;
  return `
    <!-- Message de bienvenue -->
    <div class="welcome-banner">
      <div class="welcome-left">
        <p class="welcome-sub">Bonjour 👋</p>
        <h2 class="welcome-name">${escapeHtml(student.name)}</h2>
        <p class="welcome-info">${escapeHtml(student.filiere)} · Année ${escapeHtml(student.annee)}</p>
      </div>
      <div class="avatar-circle">${escapeHtml(student.avatar)}</div>
    </div>

    <!-- Raccourci Emploi du temps -->
    <div class="quick-action-card" id="btnGoTimetable">
      <div class="qa-icon-wrap">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
             stroke="currentColor" stroke-width="2" width="24" height="24">
          <circle cx="12" cy="12" r="10"/>
          <polyline points="12 6 12 12 16 14"/>
        </svg>
      </div>
      <div class="qa-text">
        <h4>Consulter mon emploi du temps</h4>
        <p>Planning hebdomadaire des cours (Lundi - Samedi)</p>
      </div>
      <span class="qa-arrow">→</span>
    </div>

    <!-- Statistiques -->
    <div class="stats-grid">
      <div class="stat-card stat-total">
        <span class="stat-value">${stats.total}</span>
        <span class="stat-label">Total</span>
      </div>
      <div class="stat-card stat-ok">
        <span class="stat-value">${stats.justified}</span>
        <span class="stat-label">Justifiées</span>
      </div>
      <div class="stat-card stat-err">
        <span class="stat-value">${stats.unjustified}</span>
        <span class="stat-label">Non justifiées</span>
      </div>
    </div>

    <!-- Barre de progression -->
    <div class="progress-section">
      <div class="progress-header">
        <span class="progress-title">Taux d'absence</span>
        <span class="progress-pct">${rate === null ? '—' : `${Math.round(rate)} %`}</span>
      </div>
      <div class="progress-bar-bg">
        <div class="progress-bar-fill" style="width:${rate === null ? 0 : Math.min(rate, 100)}%"></div>
      </div>
      <p class="progress-hint">${escapeHtml(stats.explanation || (rate === null
    ? "Taux non disponible : aucune séance prévue n’est enregistrée pour cette période de référence."
    : `Taux calculé : ${stats.distinctMissedSessions ?? stats.total} séance(s) manquée(s) sur ${stats.plannedSessions} séance(s) prévues (${stats.referencePeriod || "Semestre 1 2026-2027"}).`))}</p>
    </div>

    <!-- Absences récentes -->
    <section class="section-block">
      <div class="section-header">
        <h3 class="section-title">Absences récentes</h3>
        <button class="link-btn" data-tab="absences">Voir tout</button>
      </div>
      <div class="absence-list">
        ${recent.length
    ? recent.map(a => absenceCard(a)).join('')
    : '<div class="empty-state"><p>Aucune absence enregistrée.</p></div>'}
      </div>
    </section>
  `;
}

function renderAbsencesTab(absences) {
  return `
    <div class="page-header-block">
      <h2 class="page-title">Mes absences</h2>
      <span class="count-badge">${absences.length}</span>
    </div>

    <!-- Filtre rapide -->
    <div class="filter-bar">
      <button class="filter-btn filter-active" data-filter="all">Toutes</button>
      <button class="filter-btn" data-filter="justified">Justifiées</button>
      <button class="filter-btn" data-filter="unjustified">Non justifiées</button>
    </div>

    <div class="absence-list" id="absenceListContainer">
      ${absences.length
    ? absences.map(a => absenceCardFull(a)).join('')
    : '<div class="empty-state"><p>Aucune absence enregistrée.</p></div>'}
    </div>
  `;
}

function renderDocsTab(absences, justifications) {
  const pendingAbsenceIds = new Set(justifications
    .filter(doc => doc.status === 'pending')
    .map(doc => String(doc.absenceId)));
  const pendingCount = pendingAbsenceIds.size;
  const needDocs = absences.filter(a =>
    a.statut === 'unjustified' && !pendingAbsenceIds.has(String(a.id))
  );
  const allAbsencesJustified = absences.length > 0
    && absences.every(absence => absence.statut === 'justified');
  const absenceById = new Map(absences.map(absence => [String(absence.id), absence]));

  return `
    <div class="page-header-block">
      <h2 class="page-title">Dépôt & Suivi des justificatifs</h2>
    </div>

    <div class="info-banner">
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" stroke-width="2" width="18" height="18">
        <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/>
        <line x1="12" y1="16" x2="12.01" y2="16"/>
      </svg>
      <span>Déposez un fichier PDF, JPG ou PNG de 10 Mo maximum. Le fichier est envoyé de manière sécurisée et conservé sur le serveur.</span>
    </div>

    <div id="docActionError" class="form-alert" role="alert" hidden></div>

    <!-- Zone formulaire de dépôt (modal ou inline rétractable) -->
    <div id="uploadModal" class="upload-modal" hidden>
      <div class="upload-card">
        <div class="upload-header">
          <h3 id="uploadModalTitle">Déposer un justificatif</h3>
          <button type="button" class="btn-close-modal" id="closeUploadModal">✕</button>
        </div>
        <form id="docUploadForm" novalidate>
          <input type="hidden" id="uploadAbsenceId" />
          <div class="upload-info-box">
            <p><strong>Matière :</strong> <span id="uploadMatiereText">-</span></p>
            <p><strong>Date d'absence :</strong> <span id="uploadDateText">-</span></p>
          </div>

          <div class="form-group" style="margin-top: 12px;">
            <label for="docFileInput">Choisir un fichier (PDF, JPG, PNG)</label>
            <input type="file" id="docFileInput" accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png" style="display:none;" />
            <button type="button" class="btn-choose-file" id="btnChooseFile">
              📁 Sélectionner un document
            </button>
            <div id="selectedFileInfo" class="selected-file-info" hidden>
              <span>📄 <span id="selectedFileName">aucun</span></span>
            </div>
            <span class="field-error" id="fileError" role="alert" aria-live="polite"></span>
          </div>

          <button type="submit" class="btn-login" style="margin-top: 14px;" id="btnSubmitDoc">
            Envoyer le justificatif
          </button>
        </form>
      </div>
    </div>

    <!-- Message de confirmation -->
    <div id="docSuccessAlert" class="form-alert-success" hidden>
      <span>✅ Justificatif envoyé avec succès (Statut initial : En attente).</span>
    </div>

    <!-- Absences à justifier -->
    <h3 class="section-title" style="margin: 20px 0 10px;">Absences à justifier (${needDocs.length})</h3>

    ${needDocs.length === 0
      ? `<div class="empty-state">
           <div class="empty-icon">🎉</div>
           <p>${allAbsencesJustified
    ? 'Toutes vos absences sont justifiées !'
    : absences.length === 0
      ? 'Aucune absence à justifier pour le moment.'
      : pendingCount > 0
        ? 'Un justificatif est déjà en attente de traitement pour chaque absence éligible. Un nouveau dépôt n’est pas possible pendant cette attente.'
        : 'Aucune absence ne peut actuellement recevoir un nouveau justificatif.'}</p>
         </div>`
      : needDocs.map(a => `
          <div class="doc-card">
            <div class="doc-info">
              <p class="doc-matiere">${escapeHtml(a.matiere)}</p>
              <p class="doc-date">${escapeHtml(formatDate(a.date))} · ${escapeHtml(a.seance)}</p>
            </div>
            <button class="btn-upload btn-open-upload" data-id="${a.id}" data-matiere="${escapeHtml(a.matiere)}" data-date="${escapeHtml(formatDate(a.date))}">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="2" width="16" height="16">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/>
              </svg>
              Déposer un justificatif
            </button>
          </div>
        `).join('')
    }

    <!-- Suivi des justificatifs déposés -->
    <h3 class="section-title" style="margin: 24px 0 10px;">Mes justificatifs (${justifications.length})</h3>
    
    ${justifications.length === 0
      ? `<div class="empty-state">
           <p>Aucun justificatif déposé pour le moment.</p>
         </div>`
      : `<div class="stored-docs-list">
          ${justifications.map(doc => {
    const absence = absenceById.get(String(doc.absenceId));
    return `
            <div class="stored-doc-card">
              <div class="sdoc-header">
                <span class="sdoc-matiere">${escapeHtml(absence?.matiere || 'Absence')}</span>
                ${docStatusBadge(doc.status)}
              </div>
              <p class="sdoc-file">📎 ${escapeHtml(doc.fileName)}</p>
              <div class="sdoc-footer">
                <span>Date absence : ${escapeHtml(formatDate(absence?.date))}</span>
                <span>Déposé le : ${escapeHtml(doc.uploadDate)}</span>
              </div>
              ${hasStoredFile(doc.filePath)
                ? `<button type="button" class="btn-upload btn-download-justification" data-docid="${escapeHtml(doc.id)}"
                           data-filename="${escapeHtml(doc.fileName)}">Télécharger le justificatif</button>`
                : ''}
            </div>
          `;
  }).join('')}
         </div>`
    }
  `;
}

function renderProfileTab(student, stats) {
  return `
    <div class="profile-header">
      <div class="profile-avatar">${escapeHtml(student.avatar)}</div>
      <h2 class="profile-name">${escapeHtml(student.name)}</h2>
      <p class="profile-email">${escapeHtml(student.email)}</p>
    </div>

    <div class="profile-card">
      <div class="profile-row"><span>Filière</span><strong>${escapeHtml(student.filiere)}</strong></div>
      <div class="profile-row"><span>Année universitaire</span><strong>${escapeHtml(student.annee)}</strong></div>
      <div class="profile-row"><span>Total absences</span><strong>${stats.total}</strong></div>
      <div class="profile-row"><span>Justifiées</span><strong>${stats.justified}</strong></div>
      <div class="profile-row"><span>Non justifiées</span><strong>${stats.unjustified}</strong></div>
    </div>

    <button class="btn-logout" id="logoutBtn">
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" stroke-width="2" width="18" height="18">
        <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
        <polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>
      </svg>
      Se déconnecter
    </button>
  `;
}

// ── Composants de carte d'absence ─────────────────────────────────────────

function absenceCard(a) {
  return `
    <div class="absence-card">
      <div class="absence-date-col">
        <span class="abs-day">${new Date(a.date).toLocaleDateString('fr-FR', { day: '2-digit' })}</span>
        <span class="abs-month">${new Date(a.date).toLocaleDateString('fr-FR', { month: 'short' })}</span>
      </div>
      <div class="absence-info">
        <p class="abs-matiere">${escapeHtml(a.matiere)}</p>
        <p class="abs-seance">${escapeHtml(a.seance)}</p>
      </div>
      <span class="badge ${statusClass(a.statut)}">${statusLabel(a.statut)}</span>
    </div>
  `;
}

function absenceCardFull(a) {
  return `
    <div class="absence-card-full" data-id="${a.id}">
      <div class="acf-top">
        <div>
          <p class="abs-matiere">${escapeHtml(a.matiere)}</p>
          <p class="abs-seance">${escapeHtml(a.seance)} · ${escapeHtml(a.enseignant)}</p>
        </div>
        <span class="badge ${statusClass(a.statut)}">${statusLabel(a.statut)}</span>
      </div>
      <div class="acf-bottom">
        <span class="acf-date">📅 ${formatDate(a.date)}</span>
        <button class="btn-detail" data-id="${a.id}">Détails →</button>
      </div>
    </div>
  `;
}

// ── Icônes SVG inline ──────────────────────────────────────────────────────

function iconHome() {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
    stroke="currentColor" stroke-width="2" width="22" height="22">
    <path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
    <polyline points="9 22 9 12 15 12 15 22"/>
  </svg>`;
}
function iconCalendar() {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
    stroke="currentColor" stroke-width="2" width="22" height="22">
    <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
    <line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/>
    <line x1="3" y1="10" x2="21" y2="10"/>
  </svg>`;
}
function iconDoc() {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
    stroke="currentColor" stroke-width="2" width="22" height="22">
    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
    <polyline points="14 2 14 8 20 8"/>
    <line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>
    <polyline points="10 9 9 9 8 9"/>
  </svg>`;
}
function iconUser() {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
    stroke="currentColor" stroke-width="2" width="22" height="22">
    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
    <circle cx="12" cy="7" r="4"/>
  </svg>`;
}

// ── Rendu principal du dashboard ───────────────────────────────────────────

export async function renderStudentDashboard(container) {
  // Écran de chargement
  container.innerHTML = `
    <div class="loading-screen">
      <div class="loading-spinner"></div>
      <p>Chargement de vos données…</p>
    </div>
  `;

  if (AppState.currentUser?.role !== 'student') {
    clearAccessToken();
    AppState.navigate('login');
    return;
  }

  let studentData;
  try {
    studentData = await fetchStudentDashboardData();
  } catch (error) {
    console.error('Impossible de charger les données du compte étudiant.', error);
    container.innerHTML = `
      <div class="loading-screen">
        <p role="alert">Impossible de charger vos données : ${escapeHtml(error.message)}</p>
        <button class="btn-login" id="retryStudentLoad">Réessayer</button>
        <button class="btn-back-bottom" id="studentLoadLogout">Retour à la connexion</button>
      </div>
    `;
    document.getElementById('retryStudentLoad')?.addEventListener('click', () => {
      renderStudentDashboard(container);
    });
    document.getElementById('studentLoadLogout')?.addEventListener('click', () => {
      clearAccessToken();
      AppState.currentUser = null;
      AppState.navigate('login');
    });
    return;
  }
  let { student, stats, absences, justifications } = studentData;
  AppState.studentData = studentData;

  // Si on arrive avec une intention de dépôt (depuis la page de détails)
  let activeTab = AppState.targetAbsenceForUpload ? 'docs' : 'home';

  function mount(tab) {
    activeTab = tab;
    let content = '';
    switch (tab) {
      case 'home':      content = renderHomeTab(student, stats, absences); break;
      case 'absences':  content = renderAbsencesTab(absences); break;
      case 'docs':      content = renderDocsTab(absences, justifications); break;
      case 'profile':   content = renderProfileTab(student, stats); break;
    }

    container.innerHTML = `
      <div class="dashboard-shell">
        <!-- Topbar -->
        <header class="topbar">
          <div class="topbar-left">
            <div class="topbar-logo">UA</div>
            <span class="topbar-title">UniAbsence</span>
          </div>
          <button class="topbar-notif" aria-label="Notifications">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2" width="22" height="22">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
              <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
            </svg>
            <span class="notif-dot"></span>
          </button>
        </header>

        <!-- Contenu scrollable -->
        <main class="dash-content">
          ${content}
        </main>

        <!-- Barre de navigation -->
        ${renderNavBar(tab)}
      </div>
    `;

    bindEvents(tab, absences);
  }

  function bindEvents(tab, absences) {
    // Accès rapide Emploi du temps (depuis l'Accueil)
    const btnGoTimetable = document.getElementById('btnGoTimetable');
    if (btnGoTimetable) {
      btnGoTimetable.addEventListener('click', () => {
        AppState.navigate('timetable');
      });
    }

    // Navigation bottom bar
    document.querySelectorAll('.nav-item').forEach(btn => {
      btn.addEventListener('click', () => mount(btn.dataset.tab));
    });

    // Lien "Voir tout" depuis home
    document.querySelectorAll('[data-tab]').forEach(btn => {
      if (!btn.classList.contains('nav-item')) {
        btn.addEventListener('click', () => mount(btn.dataset.tab));
      }
    });

    // Déconnexion
    const logout = document.getElementById('logoutBtn');
    if (logout) {
      logout.addEventListener('click', () => {
        clearAccessToken();
        AppState.currentUser = null;
        AppState.studentData = null;
        AppState.navigate('login');
      });
    }

    // Onglet Absences : filtre et boutons détails
    if (tab === 'absences') {
      const bindDetailButtons = () => {
        document.querySelectorAll('.btn-detail').forEach(b => {
          b.addEventListener('click', () => {
            const found = absences.find(a => String(a.id) === String(b.dataset.id));
            if (found) {
              AppState.selectedAbsence = found;
              AppState.navigate('absence-details');
            }
          });
        });
      };

      document.querySelectorAll('.filter-btn').forEach(btn => {
        btn.addEventListener('click', () => {
          document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('filter-active'));
          btn.classList.add('filter-active');
          const f = btn.dataset.filter;
          const filtered = f === 'all' ? absences : absences.filter(a => a.statut === f);
          document.getElementById('absenceListContainer').innerHTML =
            (filtered.length
              ? filtered.map(a => absenceCardFull(a)).join('')
              : '<div class="empty-state"><p>Aucune absence pour ce filtre.</p></div>');
          
          bindDetailButtons();
        });
      });

      bindDetailButtons();
    }

    // Onglet Justificatifs : gestion du formulaire de dépôt
    if (tab === 'docs') {
      const modal = document.getElementById('uploadModal');
      const closeModal = document.getElementById('closeUploadModal');
      const uploadMatiere = document.getElementById('uploadMatiereText');
      const uploadDate = document.getElementById('uploadDateText');
      const absenceIdInput = document.getElementById('uploadAbsenceId');
      const fileInput = document.getElementById('docFileInput');
      const btnChoose = document.getElementById('btnChooseFile');
      const fileInfo = document.getElementById('selectedFileInfo');
      const fileNameSpan = document.getElementById('selectedFileName');
      const fileError = document.getElementById('fileError');
      const form = document.getElementById('docUploadForm');
      const successAlert = document.getElementById('docSuccessAlert');
      const actionError = document.getElementById('docActionError');

      let currentSelectedFile = null;
      document.querySelectorAll('.btn-download-justification').forEach(button => {
        button.addEventListener('click', async () => {
          button.disabled = true;
          actionError.hidden = true;
          try {
            await downloadStudentJustification(button.dataset.docid, button.dataset.filename);
          } catch (error) {
            actionError.textContent = error.message || 'Impossible de télécharger le justificatif.';
            actionError.hidden = false;
          } finally {
            button.disabled = false;
          }
        });
      });

      // Si une absence cible provient de la page Détails, ouvrir immédiatement la modale
      if (AppState.targetAbsenceForUpload) {
        const target = AppState.targetAbsenceForUpload;
        AppState.targetAbsenceForUpload = null; // consommé
        absenceIdInput.value = target.id;
        uploadMatiere.textContent = target.matiere;
        uploadDate.textContent = formatDate(target.date);
        modal.hidden = false;
        setTimeout(() => modal.scrollIntoView({ behavior: 'smooth' }), 100);
      }

      // Boutons Déposer sur chaque absence non justifiée
      document.querySelectorAll('.btn-open-upload').forEach(b => {
        b.addEventListener('click', () => {
          const absId = b.dataset.id;
          const mat = b.dataset.matiere;
          const dt = b.dataset.date;

          absenceIdInput.value = absId;
          uploadMatiere.textContent = mat;
          uploadDate.textContent = dt;
          currentSelectedFile = null;
          fileInput.value = '';
          fileInfo.hidden = true;
          fileError.textContent = '';

          modal.hidden = false;
          modal.scrollIntoView({ behavior: 'smooth' });
        });
      });

      if (closeModal) {
        closeModal.addEventListener('click', () => {
          modal.hidden = true;
        });
      }

      if (btnChoose && fileInput) {
        btnChoose.addEventListener('click', () => {
          fileInput.click();
        });

        fileInput.addEventListener('change', (e) => {
          if (e.target.files && e.target.files.length > 0) {
            const selectedFile = e.target.files[0];
            const extension = selectedFile.name.split('.').pop()?.toLowerCase();
            if (!['pdf', 'jpg', 'jpeg', 'png'].includes(extension)) {
              currentSelectedFile = null;
              fileInput.value = '';
              fileInfo.hidden = true;
              fileError.textContent = 'Seuls les fichiers PDF, JPG et PNG sont acceptés.';
              return;
            }
            if (selectedFile.size === 0) {
              currentSelectedFile = null;
              fileInput.value = '';
              fileInfo.hidden = true;
              fileError.textContent = 'Le fichier sélectionné est vide.';
              return;
            }
            if (selectedFile.size > 10 * 1024 * 1024) {
              currentSelectedFile = null;
              fileInput.value = '';
              fileInfo.hidden = true;
              fileError.textContent = 'La taille maximale du fichier est de 10 Mo.';
              return;
            }
            currentSelectedFile = selectedFile;
            fileNameSpan.textContent = currentSelectedFile.name;
            fileInfo.hidden = false;
            fileError.textContent = '';
          }
        });
      }

      if (form) {
        form.addEventListener('submit', async (e) => {
          e.preventDefault();
          fileError.textContent = '';
          if (!currentSelectedFile) {
            fileError.textContent = 'Veuillez sélectionner un fichier (PDF, JPG ou PNG).';
            return;
          }

          const submitButton = document.getElementById('btnSubmitDoc');
          submitButton.disabled = true;
          submitButton.textContent = 'Envoi…';
          try {
            const uploadedJustification = await submitStudentJustification(
              absenceIdInput.value,
              currentSelectedFile
            );
            AppState.targetAbsenceForUpload = null;
            currentSelectedFile = null;
            try {
              studentData = await fetchStudentDashboardData();
              ({ student, stats, absences, justifications } = studentData);
              AppState.studentData = studentData;
              mount('docs');
              const alertBox = document.getElementById('docSuccessAlert');
              if (alertBox) {
                alertBox.hidden = false;
                setTimeout(() => {
                  if (alertBox.isConnected) alertBox.hidden = true;
                }, 4000);
              }
            } catch (refreshError) {
              const uploadedDate = new Date(uploadedJustification.submittedAt);
              const uploadDate = Number.isNaN(uploadedDate.getTime())
                ? uploadedJustification.submittedAt
                : uploadedDate.toLocaleDateString('fr-FR');
              justifications = [
                { ...uploadedJustification, uploadDate },
                ...justifications,
              ];
              studentData = { student, stats, absences, justifications };
              AppState.studentData = studentData;
              mount('docs');
              const errorAlert = document.getElementById('docActionError');
              if (errorAlert) {
                errorAlert.textContent = `Justificatif envoyé. Actualisation des données impossible : ${refreshError.message}`;
                errorAlert.hidden = false;
              }
            }
          } catch (error) {
            fileError.textContent = error.message || 'Impossible d’envoyer le justificatif.';
            submitButton.disabled = false;
            submitButton.textContent = 'Envoyer le justificatif';
          }
        });
      }
    }
  }

  mount(activeTab);
}
