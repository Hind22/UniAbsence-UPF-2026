/**
 * Tableau de bord Enseignant – UniAbsence (TP 3 Agile/Scrum)
 * Permet de visualiser les groupes, leurs effectifs et d'accéder
 * à la liste détaillée des étudiants par groupe.
 *
 * Les données sont chargées depuis les endpoints API autorisés pour l'enseignant.
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

export async function renderTeacherDashboard(container) {
  // Écran de chargement
  container.innerHTML = `
    <div class="loading-screen">
      <div class="loading-spinner"></div>
      <p>Chargement de l'espace enseignant…</p>
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
    console.error('Impossible de charger les données enseignant.', error);
    container.innerHTML = `
      <div class="loading-screen">
        <p role="alert">Impossible de charger vos données : ${escapeHtml(error.message)}</p>
        <button class="btn-login" id="retryTeacherLoad">Réessayer</button>
        <button class="btn-back-bottom" id="teacherLoadLogout">Retour à la connexion</button>
      </div>
    `;
    document.getElementById('retryTeacherLoad')?.addEventListener('click', () => {
      renderTeacherDashboard(container);
    });
    document.getElementById('teacherLoadLogout')?.addEventListener('click', () => {
      clearAccessToken();
      AppState.currentUser = null;
      AppState.navigate('login');
    });
    return;
  }
  const { teacher, groups, studentsByGroup, justifications } = teacherData;
  AppState.teacherData = teacherData;
  const totalStudents = Object.values(studentsByGroup).reduce((sum, students) => sum + students.length, 0);
  const pendingDocsCount = justifications.filter(doc => doc.status === 'pending').length;

  container.innerHTML = `
    <div class="dashboard-shell">

      <!-- Topbar Enseignant -->
      <header class="topbar">
        <div class="topbar-left">
          <div class="topbar-logo" style="background: rgba(255,255,255,.28);">ENS</div>
          <span class="topbar-title">Espace Enseignant</span>
        </div>
        <button class="topbar-logout-mini" id="teacherLogoutTop" aria-label="Déconnexion" title="Déconnexion">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
               stroke="currentColor" stroke-width="2" width="20" height="20">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
            <polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>
          </svg>
        </button>
      </header>

      <!-- Contenu scrollable -->
      <main class="dash-content">

        <!-- Carte de profil enseignant -->
        <div class="welcome-banner" style="background: linear-gradient(135deg, #1E40AF 0%, #1D4ED8 100%);">
          <div class="welcome-left">
            <p class="welcome-sub">Bienvenue 👋</p>
            <h2 class="welcome-name">${escapeHtml(teacher.name)}</h2>
            <p class="welcome-info">${escapeHtml(teacher.grade)} · Dépt. ${escapeHtml(teacher.departement)}</p>
            <p class="welcome-info" style="margin-top: 2px;">Matière : <strong>${escapeHtml(teacher.matiere)}</strong></p>
          </div>
          <div class="avatar-circle" style="background: rgba(255,255,255,.25); font-size: 1.15rem;">
            ${escapeHtml(teacher.avatar)}
          </div>
        </div>

        <!-- Statistiques Enseignant -->
        <div class="stats-grid">
          <div class="stat-card stat-total">
            <span class="stat-value">${groups.length}</span>
            <span class="stat-label">Groupes</span>
          </div>
          <div class="stat-card stat-ok">
            <span class="stat-value">${totalStudents}</span>
            <span class="stat-label">Étudiants</span>
          </div>
          <div class="stat-card stat-err">
            <span class="stat-value">${pendingDocsCount}</span>
            <span class="stat-label">Justif. reçus</span>
          </div>
        </div>

        <!-- Accès direct à la liste des étudiants -->
        <div class="quick-action-card" id="btnGoTeacherStudents">
          <div class="qa-icon-wrap" style="background: #EFF6FF; color: var(--blue-600);">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2" width="24" height="24">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
          </div>
          <div class="qa-text">
            <h4>Consulter la liste des étudiants</h4>
            <p>Sélectionnez un groupe et visualisez les situations d'absence</p>
          </div>
          <span class="qa-arrow">→</span>
        </div>

        <!-- Accès interactif : Gestion & saisie des absences -->
        <div class="quick-action-card" id="btnGoManageAbsences" style="border-color: #60A5FA; background: linear-gradient(135deg, #FFFFFF, #F0FDF4);">
          <div class="qa-icon-wrap" style="background: #DCFCE7; color: var(--green-700);">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2" width="24" height="24">
              <path d="M12 20h9"/>
              <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/>
            </svg>
          </div>
          <div class="qa-text">
              <h4>Gestion des absences & Justificatifs</h4>
              <p>Enregistrer une absence ou traiter les justificatifs reçus</p>
          </div>
          <span class="qa-arrow">→</span>
        </div>

        <!-- Groupes sous responsabilité -->
        <section class="section-block" style="margin-top: 16px;">
          <div class="section-header">
            <h3 class="section-title">Mes groupes (${groups.length})</h3>
            <span class="count-badge">${totalStudents} étudiants au total</span>
          </div>

          <div class="teacher-groups-list">
            ${groups.length ? groups.map(g => `
              <div class="group-card" data-gid="${escapeHtml(g.id)}">
                <div class="group-info">
                  <h4 class="group-name">${escapeHtml(g.nom)}</h4>
                  <p class="group-spec">${escapeHtml(g.specialite)} · ${escapeHtml(g.matiere)}</p>
                </div>
                <div class="group-right">
                  <span class="group-count">👥 ${g.effectif} étud.</span>
                  <button class="btn-group-view" data-gid="${escapeHtml(g.id)}">Voir la liste →</button>
                </div>
              </div>
            `).join('') : '<p class="empty-state" role="alert">Votre profil enseignant est actif, mais aucun groupe ne vous est actuellement affecté. Contactez l’administration pour obtenir une affectation.</p>'}
          </div>
        </section>

        <!-- Bouton Déconnexion -->
        <button class="btn-logout" id="teacherLogoutBtn" style="margin-top: 24px;">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
               stroke="currentColor" stroke-width="2" width="18" height="18">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
            <polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>
          </svg>
          Se déconnecter de l'espace enseignant
        </button>

      </main>

    </div>
  `;

  // ── Gestion des événements ───────────────────────────────────────────────
  const logoutAction = () => {
    clearAccessToken();
    AppState.currentUser = null;
    AppState.teacherData = null;
    AppState.navigate('login');
  };

  document.getElementById('teacherLogoutTop')?.addEventListener('click', logoutAction);
  document.getElementById('teacherLogoutBtn')?.addEventListener('click', logoutAction);

  // Accès rapide à la liste des étudiants
  document.getElementById('btnGoTeacherStudents')?.addEventListener('click', () => {
    AppState.activeTeacherGroup = groups[0]?.id || null;
    AppState.navigate('teacher-students');
  });

  // Accès à la gestion des absences
  document.getElementById('btnGoManageAbsences')?.addEventListener('click', () => {
    AppState.activeTeacherGroup = groups[0]?.id || null;
    AppState.navigate('teacher-manage-absences');
  });

  // Clic sur un groupe spécifique
  document.querySelectorAll('.btn-group-view, .group-card').forEach(el => {
    el.addEventListener('click', (e) => {
      e.stopPropagation();
      const gid = el.dataset.gid || el.closest('.group-card')?.dataset.gid || groups[0]?.id;
      if (!groups.some(group => group.id === gid)) return;
      AppState.activeTeacherGroup = gid;
      AppState.navigate('teacher-students');
    });
  });
}
