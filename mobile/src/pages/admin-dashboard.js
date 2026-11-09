/**
 * Page d'Accueil Administration – UniAbsence (TP 3 Agile/Scrum)
 *
 * Affiche :
 * - Message de bienvenue avec profil administrateur scolarité.
 * - Synthèse chiffrée globale (Étudiants, Enseignants, Groupes, Absences globales).
 * - Les 4 rubriques principales demandées :
 *   1. Gestion des étudiants
 *   2. Gestion des enseignants
 *   3. Gestion des groupes
 *   4. Statistiques des absences
 * - Bouton de déconnexion ramenant à la page de connexion.
 *
 */
import { AppState } from '../router.js';
import { clearAccessToken } from '../api/client.js';
import { fetchAdminData, requireAdmin } from '../api/admin.js';

export async function renderAdminDashboard(container) {
  // Écran de chargement
  container.innerHTML = `
    <div class="loading-screen">
      <div class="loading-spinner"></div>
      <p>Chargement de l'espace administration…</p>
    </div>
  `;

  let data;
  try {
    requireAdmin();
    data = await fetchAdminData();
  } catch (error) {
    const escape = value => String(value ?? '').replace(/[&<>"']/g, character => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    })[character]);
    container.innerHTML = `
      <div class="dashboard-shell"><header class="topbar"><span class="topbar-title">Administration</span></header>
        <main class="dash-content"><div class="form-alert" role="alert">${escape(error.message)}</div><button class="btn-login" id="adminDashboardRetry">Réessayer</button></main>
      </div>`;
    document.getElementById('adminDashboardRetry')?.addEventListener('click', () => renderAdminDashboard(container));
    return;
  }
  const admin = data.admin;

  // Statistiques globales de l'établissement
  const totalStudents = data.students.length;
  const totalTeachers = data.teachers.length;
  const totalGroups = data.groups.length;
  const totalAbsencesCount = data.absences.length;
  const escape = value => String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  })[character]);

  container.innerHTML = `
    <div class="dashboard-shell">

      <!-- Topbar Administration -->
      <header class="topbar">
        <div class="topbar-left">
          <div class="topbar-logo" style="background: rgba(255,255,255,.28); letter-spacing:.5px;">ADM</div>
          <span class="topbar-title">Administration</span>
        </div>
        <button class="topbar-logout-mini" id="adminLogoutTop" aria-label="Déconnexion" title="Déconnexion">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
               stroke="currentColor" stroke-width="2" width="20" height="20">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
            <polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>
          </svg>
        </button>
      </header>

      <!-- Contenu scrollable -->
      <main class="dash-content">

        <!-- Carte de bienvenue Administrateur -->
        <div class="welcome-banner" style="background: linear-gradient(135deg, #0F172A 0%, #1E3A8A 100%);">
          <div class="welcome-left">
            <p class="welcome-sub">Session Administrative 🏛️</p>
            <h2 class="welcome-name">${escape(admin?.name || admin?.email || 'Administrateur')}</h2>
            <p class="welcome-info">${escape(admin?.email || 'Compte administrateur')}</p>
          </div>
          <div class="avatar-circle" style="background: rgba(255,255,255,.2); font-size: 1.15rem;">
            ADM
          </div>
        </div>

        <!-- Chiffres clés globaux -->
        <div class="stats-grid" style="grid-template-columns: repeat(4, 1fr); gap: 6px;">
          <div class="stat-card stat-total" style="padding: 10px 4px;">
            <span class="stat-value" style="font-size: 1.35rem;">${totalStudents}</span>
            <span class="stat-label" style="font-size: .62rem;">Étudiants</span>
          </div>
          <div class="stat-card stat-ok" style="padding: 10px 4px;">
            <span class="stat-value" style="font-size: 1.35rem;">${totalTeachers}</span>
            <span class="stat-label" style="font-size: .62rem;">Enseignants</span>
          </div>
          <div class="stat-card" style="padding: 10px 4px; background: #FEF3C7;">
            <span class="stat-value" style="font-size: 1.35rem; color: #92400E;">${totalGroups}</span>
            <span class="stat-label" style="font-size: .62rem;">Groupes</span>
          </div>
          <div class="stat-card stat-err" style="padding: 10px 4px;">
            <span class="stat-value" style="font-size: 1.35rem;">${totalAbsencesCount}</span>
            <span class="stat-label" style="font-size: .62rem;">Absences</span>
          </div>
        </div>

        <!-- Les 4 Rubriques d'Administration -->
        <h3 class="section-title" style="margin: 20px 0 12px; font-size: .95rem;">
          Modules de gestion administrative
        </h3>

        <div class="admin-modules-grid">

          <!-- 1. Gestion des étudiants (Opérationnel) -->
          <div class="admin-module-card" data-module="students" id="btnAdminModuleStudents">
            <div class="amc-icon-wrap" style="background: #EFF6FF; color: var(--blue-600);">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="2" width="24" height="24">
                <path d="M22 10v6M2 10l10-5 10 5-10 5z"/>
                <path d="M6 12v5c3 3 9 3 12 0v-5"/>
              </svg>
            </div>
            <div class="amc-content">
              <h4>Gestion des étudiants</h4>
              <p>Inscriptions, affectations aux groupes et dossiers scolaires</p>
            </div>
            <span class="amc-tag" style="background: #DCFCE7; color: #15803D;">✅ Actif</span>
          </div>

          <!-- 2. Gestion des enseignants -->
          <div class="admin-module-card admin-module-card-active" data-module="teachers" id="btnAdminModuleTeachers" role="button" tabindex="0">
            <div class="amc-icon-wrap" style="background: #F0FDF4; color: var(--green-700);">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="2" width="24" height="24">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
            </div>
            <div class="amc-content">
              <h4>Gestion des enseignants</h4>
              <p>Corps professoral, charges horaires et matières enseignées</p>
            </div>
            <span class="amc-tag" style="background: #DCFCE7; color: #15803D;">✅ Actif</span>
          </div>

          <!-- 3. Gestion des groupes -->
          <div class="admin-module-card admin-module-card-active" data-module="groups" id="btnAdminModuleGroups" role="button" tabindex="0">
            <div class="amc-icon-wrap" style="background: #FEF3C7; color: #92400E;">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="2" width="24" height="24">
                <rect x="3" y="3" width="7" height="7"/>
                <rect x="14" y="3" width="7" height="7"/>
                <rect x="14" y="14" width="7" height="7"/>
                <rect x="3" y="14" width="7" height="7"/>
              </svg>
            </div>
            <div class="amc-content">
              <h4>Gestion des groupes</h4>
              <p>Création des promotions, filières et répartition des effectifs</p>
            </div>
            <span class="amc-tag" style="background: #DCFCE7; color: #15803D;">✅ Actif</span>
          </div>

          <!-- 4. Statistiques des absences -->
          <div class="admin-module-card admin-module-card-active" data-module="stats" id="btnAdminModuleStats" role="button" tabindex="0">
            <div class="amc-icon-wrap" style="background: #FEE2E2; color: var(--red-700);">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="2" width="24" height="24">
                <line x1="18" y1="20" x2="18" y2="10"/>
                <line x1="12" y1="20" x2="12" y2="4"/>
                <line x1="6" y1="20" x2="6" y2="14"/>
              </svg>
            </div>
            <div class="amc-content">
              <h4>Statistiques des absences</h4>
              <p>Rapports semestriels, seuils d'exclusion et taux de présence</p>
            </div>
            <span class="amc-tag" style="background: #DCFCE7; color: #15803D;">✅ Actif</span>
          </div>

        </div>

        <!-- Message d'information de phase progressive -->
        <div class="info-banner" style="margin-top: 18px;">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
            <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
          <span>Les quatre modules principaux de l’administration sont disponibles.</span>
        </div>

        <!-- Bouton Déconnexion -->
        <button class="btn-logout" id="adminLogoutBtn" style="margin-top: 24px;">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
               stroke="currentColor" stroke-width="2" width="18" height="18">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
            <polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>
          </svg>
          Se déconnecter de l'administration
        </button>

      </main>
    </div>
  `;

  // ── Gestion des événements ───────────────────────────────────────────────
  const logoutAction = () => {
    clearAccessToken();
    AppState.currentUser = null;
    AppState.teacherData = null;
    AppState.studentData = null;
    AppState.navigate('login');
  };

  document.getElementById('adminLogoutTop')?.addEventListener('click', logoutAction);
  document.getElementById('adminLogoutBtn')?.addEventListener('click', logoutAction);

  // Accès au module Gestion des étudiants
  document.getElementById('btnAdminModuleStudents')?.addEventListener('click', () => {
    AppState.navigate('admin-students');
  });

  const teachersCard = document.getElementById('btnAdminModuleTeachers');
  const openTeachers = () => AppState.navigate('admin-teachers');
  teachersCard?.addEventListener('click', openTeachers);
  teachersCard?.addEventListener('keydown', event => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      openTeachers();
    }
  });

  const groupsCard = document.getElementById('btnAdminModuleGroups');
  const openGroups = () => AppState.navigate('admin-groups');
  groupsCard?.addEventListener('click', openGroups);
  groupsCard?.addEventListener('keydown', event => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      openGroups();
    }
  });

  const statsCard = document.getElementById('btnAdminModuleStats');
  const openAbsenceStats = () => AppState.navigate('admin-absence-stats');
  statsCard?.addEventListener('click', openAbsenceStats);
  statsCard?.addEventListener('keydown', event => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      openAbsenceStats();
    }
  });
}
