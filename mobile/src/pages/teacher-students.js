/**
 * Page Liste des Étudiants d'un Groupe (Espace Enseignant)
 * Permet à l'enseignant de sélectionner un groupe, de voir le nom,
 * le prénom et le total des absences de chaque étudiant, et d'ouvrir sa situation.
 */
import { AppState } from '../router.js';
import { clearAccessToken } from '../api/client.js';
import { fetchTeacherData } from '../api/teacher.js';

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  })[character]);
}

export async function renderTeacherStudents(container) {
  container.innerHTML = `
    <div class="loading-screen">
      <div class="loading-spinner"></div>
      <p>Chargement des étudiants…</p>
    </div>
  `;

  if (AppState.currentUser?.role !== 'teacher') {
    clearAccessToken();
    AppState.navigate('login');
    return;
  }

  let teacherData;
  try {
    teacherData = await fetchTeacherData();
  } catch (error) {
    console.error('Impossible de charger les étudiants des groupes.', error);
    container.innerHTML = `
      <div class="loading-screen">
        <p role="alert">Impossible de charger les étudiants : ${escapeHtml(error.message)}</p>
        <button class="btn-login" id="retryTeacherStudents">Réessayer</button>
        <button class="btn-back-bottom" id="teacherStudentsBack">Retour à l’espace enseignant</button>
      </div>
    `;
    document.getElementById('retryTeacherStudents')?.addEventListener('click', () => {
      renderTeacherStudents(container);
    });
    document.getElementById('teacherStudentsBack')?.addEventListener('click', () => {
      AppState.navigate('teacher-dashboard');
    });
    return;
  }
  const { groups, studentsByGroup } = teacherData;
  AppState.teacherData = teacherData;
  let currentGroupId = AppState.activeTeacherGroup || groups[0]?.id || null;
  let activeStudentSituation = null;

  function mount() {
    const currentGroup = groups.find(g => g.id === currentGroupId);
    if (!currentGroup) {
      container.innerHTML = `
        <div class="dashboard-shell">
          <header class="topbar">
            <button class="topbar-back" id="btnBackToTeacherDash" aria-label="Retour au tableau de bord enseignant">‹</button>
            <span class="topbar-title">Liste des étudiants</span>
          </header>
          <main class="dash-content"><div class="empty-state"><p>Aucun groupe ne vous est affecté.</p></div></main>
        </div>
      `;
      document.getElementById('btnBackToTeacherDash')?.addEventListener('click', () => {
        AppState.navigate('teacher-dashboard');
      });
      return;
    }
    const rawStudents = studentsByGroup[currentGroupId] || [];
    const students = rawStudents;

    container.innerHTML = `
      <div class="dashboard-shell">

        <!-- Topbar -->
        <header class="topbar">
          <div class="topbar-left">
            <button class="topbar-back" id="btnBackToTeacherDash" aria-label="Retour au tableau de bord enseignant">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="2.5" width="20" height="20">
                <polyline points="15 18 9 12 15 6"/>
              </svg>
            </button>
            <span class="topbar-title">Liste des étudiants</span>
          </div>
          <span class="topbar-sub">${escapeHtml(currentGroup.nom)}</span>
        </header>

        <!-- Sélecteur d'onglets de groupe -->
        <div class="day-selector-wrap" style="padding: 10px 14px;">
          <div class="day-selector">
            ${groups.map(g => `
              <button class="day-btn ${g.id === currentGroupId ? 'day-active' : ''}"
                      data-gid="${escapeHtml(g.id)}">
                ${escapeHtml(g.id)} (${g.effectif})
              </button>
            `).join('')}
          </div>
        </div>

        <!-- Contenu scrollable -->
        <main class="dash-content">

          <!-- En-tête du groupe sélectionné -->
          <div class="group-header-banner">
            <div>
              <h3 class="ghb-title">${escapeHtml(currentGroup.nom)}</h3>
              <p class="ghb-sub">${escapeHtml(currentGroup.specialite)} · ${escapeHtml(currentGroup.matiere)}</p>
            </div>
            <span class="count-badge">${students.length} inscrits</span>
          </div>

          <!-- Modale de situation d'un étudiant sélectionné -->
          ${activeStudentSituation ? renderStudentSituationModal(activeStudentSituation) : ''}

          <!-- Liste des cartes étudiants -->
          <div class="students-list">
            ${students.length ? students.map(st => `
              <article class="student-item-card" data-sid="${st.id}">
                <div class="st-avatar">${escapeHtml(st.avatar)}</div>
                <div class="st-info">
                  <h4 class="st-name">${escapeHtml(st.nom)} ${escapeHtml(st.prenom)}</h4>
                  <p class="st-email">${escapeHtml(st.email)}</p>
                  <div class="st-tags">
                    <span class="badge ${st.absencesTotal > 4 ? 'badge-err' : (st.absencesTotal > 0 ? 'badge-pending-tag' : 'badge-ok')}">
                      ${st.absencesTotal} absence${st.absencesTotal > 1 ? 's' : ''}
                    </span>
                    ${st.pendingDocsCount > 0 ? `<span class="badge badge-doc-pending">📄 ${st.pendingDocsCount} justif. à vérifier</span>` : ''}
                  </div>
                </div>
                <button type="button" class="btn-detail btn-view-student" data-sid="${st.id}"
                        aria-label="Situation de ${escapeHtml(st.prenom)} ${escapeHtml(st.nom)}">
                  Situation →
                </button>
              </article>
            `).join('') : '<div class="empty-state"><p>Aucun étudiant dans ce groupe.</p></div>'}
          </div>

          <!-- Note bas de page -->
          <p class="tt-disclaimer" style="margin-top: 20px;">Données et autorisations fournies par le serveur.</p>
        </main>
      </div>
    `;

    bindEvents();
  }

  function renderStudentSituationModal(st) {
    const tauxAbsence = st.taux === null ? null : Number(st.taux).toFixed(1);
    const missedSessions = st.distinctMissedSessions ?? st.absencesTotal;
    const tauxExplanation = st.explanation || (tauxAbsence === null
      ? "Taux non disponible : aucune séance prévue n’est enregistrée pour cette période de référence."
      : `Taux calculé sur ${st.plannedSessions} séances prévues (${st.referencePeriod || "Semestre 1 2026-2027"}).`);

    return `
      <section class="upload-modal teacher-situation" aria-labelledby="studentSituationTitle">
        <div class="upload-header">
          <div class="situation-title-wrap">
            <p class="situation-eyebrow">Fiche étudiant</p>
            <h3 id="studentSituationTitle">Situation : ${escapeHtml(st.prenom)} ${escapeHtml(st.nom)}</h3>
          </div>
          <button type="button" class="btn-close-modal" id="btnCloseSituation" aria-label="Fermer la situation">✕</button>
        </div>

        <div class="situation-program">
          <span>Filière</span>
          <strong>${escapeHtml(st.filiere)}</strong>
        </div>

        <div class="situation-stats" aria-label="Synthèse des absences">
          <article class="situation-stat situation-stat-total">
            <span>Séances manquées</span>
            <strong>${missedSessions}</strong>
          </article>
          <article class="situation-stat situation-stat-justified">
            <span>Justifiées</span>
            <strong>${st.statsJustifiedAbsences}</strong>
          </article>
          <article class="situation-stat situation-stat-unjustified">
            <span>Non justifiées</span>
            <strong>${st.statsUnjustifiedAbsences}</strong>
          </article>
        </div>

        <section class="situation-rate ${tauxAbsence === null ? 'is-unavailable' : (Number(tauxAbsence) > 20 ? 'is-high' : '')}"
                 aria-label="Taux d’absence">
          <div class="situation-rate-heading">
            <h4>Taux d’absence</h4>
            <strong>${tauxAbsence === null ? '—' : `${tauxAbsence} %`}</strong>
          </div>
          <p>${escapeHtml(tauxExplanation)}</p>
        </section>

        ${st.pendingDocsCount > 0 ? `
          <p class="situation-pending-docs">
            <strong>Justificatifs en attente :</strong> ${st.pendingDocsCount}
          </p>
        ` : ''}

        <section class="situation-history" aria-labelledby="studentSituationHistoryTitle">
          <div class="situation-history-header">
            <h4 id="studentSituationHistoryTitle">Historique des absences <span>(${st.absences?.length || 0})</span></h4>
            <button type="button" class="link-btn" id="btnGoAddAbsenceForStudent">
            ➕ Ajouter absence
            </button>
          </div>

          ${(!st.absences || st.absences.length === 0)
            ? `<p class="situation-empty-history">Aucune absence enregistrée pour cet étudiant.</p>`
            : `<div class="mini-absence-list">
                ${st.absences.map(a => `
                  <article class="situation-absence-row">
                    <div class="situation-absence-info">
                    <strong>${escapeHtml(a.matiere)}</strong> (${escapeHtml(a.typeSeance || 'CM')})
                    <p>${escapeHtml(new Date(a.date).toLocaleDateString('fr-FR'))} · ${escapeHtml(a.seance || '')}</p>
                    </div>
                    <div class="situation-absence-actions">
                      <span class="badge ${a.statut === 'justified' ? 'badge-ok' : 'badge-err'}">
                        ${a.statut === 'justified' ? 'Justifiée' : 'Non justifiée'}
                      </span>
                      <button type="button" class="btn-group-view btn-quick-modify" data-aid="${a.id}" data-sid="${st.id}"
                              aria-label="Modifier l’absence du ${escapeHtml(new Date(a.date).toLocaleDateString('fr-FR'))}">
                      ✏️
                      </button>
                    </div>
                  </article>
                `).join('')}
              </div>`
          }
        </section>

        <button type="button" class="btn-back-bottom situation-close-bottom" id="btnCloseSituationBottom">
          Fermer la fiche
        </button>
      </section>
    `;
  }

  function bindEvents() {
    // Retour vers dashboard enseignant
    document.getElementById('btnBackToTeacherDash')?.addEventListener('click', () => {
      AppState.navigate('teacher-dashboard');
    });

    // Changement de groupe via les onglets
    document.querySelectorAll('.day-btn').forEach(b => {
      b.addEventListener('click', () => {
        currentGroupId = b.dataset.gid;
        AppState.activeTeacherGroup = currentGroupId;
        activeStudentSituation = null;
        mount();
      });
    });

    // Clic pour voir la situation d'un étudiant
    document.querySelectorAll('.btn-view-student, .student-item-card').forEach(el => {
      el.addEventListener('click', (e) => {
        e.stopPropagation();
        const sid = Number(el.dataset.sid || el.closest('.student-item-card')?.dataset.sid);
        const list = studentsByGroup[currentGroupId] || [];
        const found = list.find(s => s.id === sid);
        if (found) {
          activeStudentSituation = found;
          mount();
          window.scrollTo({ top: 0, behavior: 'smooth' });
        }
      });
    });

    // Fermeture de la modale de situation
    document.getElementById('btnCloseSituation')?.addEventListener('click', () => {
      activeStudentSituation = null;
      mount();
    });
    document.getElementById('btnCloseSituationBottom')?.addEventListener('click', () => {
      activeStudentSituation = null;
      mount();
    });

    // Raccourci ajout d'absence depuis la situation d'un étudiant
    document.getElementById('btnGoAddAbsenceForStudent')?.addEventListener('click', () => {
      AppState.activeTeacherGroup = currentGroupId;
      AppState.editingAbsence = null;
      AppState.navigate('teacher-manage-absences');
    });

    // Raccourci modification rapide depuis la situation d'un étudiant
    document.querySelectorAll('.btn-quick-modify').forEach(b => {
      b.addEventListener('click', () => {
        const aid = b.dataset.aid;
        const student = (studentsByGroup[currentGroupId] || [])
          .find(item => Number(item.id) === Number(b.dataset.sid));
        const found = student?.absences.find(a => String(a.id) === String(aid));
        if (found) {
          AppState.activeTeacherGroup = currentGroupId;
          AppState.editingAbsence = found;
          AppState.navigate('teacher-manage-absences');
        }
      });
    });
  }

  mount();
}
