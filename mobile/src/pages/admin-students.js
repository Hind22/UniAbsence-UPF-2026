/**
 * Page Gestion des Étudiants – Espace Administration (TP 3 Agile/Scrum)
 *
 * Fonctionnalités complètes :
 * 1. Afficher la liste des étudiants existants (nom, prénom, email, numéro étudiant, groupe, absences).
 * 2. Ajouter un nouvel étudiant via formulaire (nom, prénom, email, numéro étudiant, groupe).
 * 3. Modifier les informations d'un étudiant existant (avec conservation d'ID stable).
 * 4. Affecter / réaffecter un étudiant à un groupe existant.
 * 5. Rechercher dynamiquement un étudiant par nom, prénom ou numéro étudiant.
 * 6. Afficher le nombre total d'étudiants (global et filtré).
 * 7. Bouton de retour vers le tableau de bord Administration.
 */
import { AppState } from '../router.js';
import {
  deleteAdminStudent,
  createAdminAccount,
  fetchAdminData,
  requireAdmin,
  saveAdminStudent,
  updateAdminAccount,
} from '../api/admin.js';

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  })[character]);
}

export async function renderAdminStudents(container) {
  container.innerHTML = '<div class="loading-screen"><div class="loading-spinner"></div><p>Chargement des étudiants…</p></div>';
  let data;
  try {
    requireAdmin();
    data = await fetchAdminData();
  } catch (error) {
    container.innerHTML = `
      <div class="dashboard-shell"><header class="topbar"><button class="topbar-back" id="adminLoadBack">‹</button><span class="topbar-title">Gestion des étudiants</span></header>
        <main class="dash-content"><div class="form-alert" role="alert">${escapeHtml(error.message)}</div><button class="btn-login" id="adminLoadRetry">Réessayer</button></main>
      </div>`;
    document.getElementById('adminLoadBack')?.addEventListener('click', () => AppState.navigate('admin-dashboard'));
    document.getElementById('adminLoadRetry')?.addEventListener('click', () => renderAdminStudents(container));
    return;
  }
  let searchTerm = '';
  let editingStudent = AppState.editingStudentAdmin || null;
  let isFormOpen = Boolean(editingStudent);

  function mount(successMessage = '') {
    const allStudents = data.students;
    const groups = data.groups;

    // Filtrage par recherche
    const filteredStudents = allStudents.filter(st => {
      const q = searchTerm.trim().toLowerCase();
      if (!q) return true;
      const fullName = `${st.nom} ${st.prenom}`.toLowerCase();
      const reverseName = `${st.prenom} ${st.nom}`.toLowerCase();
      const num = (st.numeroEtudiant || '').toLowerCase();
      const email = (st.email || '').toLowerCase();
      return fullName.includes(q) || reverseName.includes(q) || num.includes(q) || email.includes(q);
    });

    container.innerHTML = `
      <div class="dashboard-shell">

        <!-- Topbar Administration -->
        <header class="topbar">
          <div class="topbar-left">
            <button class="topbar-back" id="btnBackToAdminDash" aria-label="Retour au tableau de bord administration">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="2.5" width="20" height="20">
                <polyline points="15 18 9 12 15 6"/>
              </svg>
            </button>
            <span class="topbar-title">Gestion des étudiants</span>
          </div>
          <span class="topbar-sub">${allStudents.length} inscrits</span>
        </header>

        <!-- Contenu scrollable -->
        <main class="dash-content">

          <!-- Alertes messages -->
          <div id="adminStudentAlertSuccess" class="form-alert-success" hidden style="display: none;">
            <span id="adminStudentAlertSuccessText">${escapeHtml(successMessage)}</span>
          </div>
          <div id="adminStudentAlertError" class="form-alert" hidden style="display: none; margin-bottom: 12px;">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            <span id="adminStudentAlertErrorText">Une erreur est survenue.</span>
          </div>

          <!-- Barre d'action supérieure : Recherche & Bouton Ajouter -->
          <div class="admin-search-bar-wrap">
            <div class="admin-search-input-box">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="2" width="18" height="18">
                <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <input type="text" id="searchStudentInput" placeholder="Rechercher par nom ou n° étudiant…"
                     value="${escapeHtml(searchTerm)}" />
              ${searchTerm ? `<button type="button" id="btnClearSearch" class="btn-clear-search">✕</button>` : ''}
            </div>
            <button type="button" class="btn-add-student" id="btnToggleAddForm">
              ${isFormOpen ? '✕ Fermer' : '➕ Nouveau'}
            </button>
          </div>

          <!-- Formulaire d'Ajout / Modification d'un étudiant -->
          <div id="studentFormWrapper" class="upload-modal" style="${isFormOpen ? 'display:block;' : 'display:none;'} margin-bottom: 16px; border-color: var(--blue-600);">
            <div class="upload-header">
              <h3 id="studentFormTitle">
                ${editingStudent ? `Modifier : ${editingStudent.prenom} ${editingStudent.nom}` : 'Inscrire un nouvel étudiant'}
              </h3>
              <button type="button" class="btn-close-modal" id="btnCloseStudentForm">✕</button>
            </div>

            <form id="studentAdminForm" class="login-form" novalidate style="gap: 12px;">
              <input type="hidden" id="studentFormId" value="${editingStudent ? editingStudent.id : ''}" />

              <div style="display:flex; gap:10px;">
                <div class="form-group" style="flex:1;">
                  <label for="inputStudentNom">Nom *</label>
                  <input type="text" id="inputStudentNom" class="input-basic" placeholder="ex. Benali"
                         value="${escapeHtml(editingStudent?.nom || '')}" required />
                </div>
                <div class="form-group" style="flex:1;">
                  <label for="inputStudentPrenom">Prénom *</label>
                  <input type="text" id="inputStudentPrenom" class="input-basic" placeholder="ex. Ahmed"
                         value="${escapeHtml(editingStudent?.prenom || '')}" required />
                </div>
              </div>

              <div class="form-group">
                <label for="inputStudentEmail">Adresse e-mail universitaire *</label>
                <input type="email" id="inputStudentEmail" class="input-basic" placeholder="ex. a.benali@univ-alger.dz"
                       value="${escapeHtml(editingStudent?.email || '')}" required />
              </div>

              <div class="form-group">
                <label for="inputStudentNum">Numéro étudiant (matricule) *</label>
                <input type="text" id="inputStudentNum" class="input-basic" placeholder="ex. ETU-2026-0001"
                       value="${escapeHtml(editingStudent?.numeroEtudiant || '')}" required />
              </div>

              <div class="form-group">
                <label for="selectStudentGroup">Affectation au groupe *</label>
                <div class="select-wrapper">
                  <select id="selectStudentGroup" required>
                    ${groups.map(g => `
                      <option value="${escapeHtml(g.id)}" ${editingStudent && editingStudent.groupeId === g.id ? 'selected' : ''}>
                        ${escapeHtml(g.nom)} — ${escapeHtml(g.specialite)}
                      </option>
                    `).join('')}
                  </select>
                </div>
              </div>

              ${editingStudent?.userId
                ? `<div class="form-group">
                    <label for="inputStudentPassword">Réinitialiser le mot de passe du compte (facultatif)</label>
                    <input type="password" id="inputStudentPassword" class="input-basic"
                           autocomplete="new-password" minlength="8" placeholder="Laisser vide pour ne pas le modifier" />
                  </div>
                  <p class="tt-disclaimer">Compte de connexion associé à ${escapeHtml(editingStudent.email)}. Aucun mot de passe existant n’est affiché.</p>`
                : `<fieldset class="teacher-groups-field" style="margin-top:12px;">
                    <label class="teacher-group-option">
                      <input type="checkbox" id="studentCreateAccount" checked />
                      <span><strong>Créer aussi le compte de connexion</strong></span>
                    </label>
                    <div class="form-group" id="studentInitialPasswordGroup" style="margin-top:10px;">
                      <label for="inputStudentPassword">Mot de passe initial *</label>
                      <input type="password" id="inputStudentPassword" class="input-basic"
                             autocomplete="new-password" minlength="8" required />
                    </div>
                    <small>Le mot de passe est transmis de manière sécurisée et stocké sous forme de hash.</small>
                  </fieldset>`}
              <button type="submit" class="btn-login" style="margin-top: 6px;" id="btnSaveStudentSubmit">
                ${editingStudent ? '💾 Enregistrer les modifications' : '➕ Valider l\'inscription'}
              </button>
            </form>
          </div>

          <!-- Total & En-tête de liste -->
          <div class="admin-list-header">
            <span class="alh-count">
              ${filteredStudents.length} étudiant${filteredStudents.length > 1 ? 's' : ''} ${searchTerm ? `trouvé(s) sur ${allStudents.length}` : 'au total'}
            </span>
            <span class="alh-sub">Profils synchronisés avec le serveur</span>
          </div>

          <!-- Liste des cartes étudiants -->
          ${filteredStudents.length === 0
            ? `<div class="empty-state">
                 <div class="empty-icon">🔍</div>
                 <p>Aucun étudiant ne correspond à votre recherche « ${escapeHtml(searchTerm)} ».</p>
                 <button class="link-btn" id="btnResetSearch">Réinitialiser la recherche</button>
               </div>`
            : `<div class="students-list">
                ${filteredStudents.map(st => `
                  <div class="student-item-card" data-sid="${st.id}">
                    <div class="st-avatar">${escapeHtml(st.avatar)}</div>
                    <div class="st-info">
                      <div class="admin-student-heading">
                        <h4 class="st-name">${escapeHtml(st.nom)} ${escapeHtml(st.prenom)}</h4>
                        <span class="st-group-tag">${escapeHtml(st.groupeId)}</span>
                      </div>
                      <p class="st-email">Matricule : <strong>${escapeHtml(st.numeroEtudiant)}</strong></p>
                      <p class="st-email" style="margin-top:1px;">✉️ ${escapeHtml(st.email)}</p>
                      <div class="st-tags" style="margin-top: 6px;">
                        <span class="badge ${st.absencesTotal > 4 ? 'badge-err' : (st.absencesTotal > 0 ? 'badge-pending-tag' : 'badge-ok')}">
                          ${st.absencesTotal} absence${st.absencesTotal > 1 ? 's' : ''} (${st.absencesJustifiees}J / ${st.absencesNonJustifiees}NJ)
                        </span>
                        <span class="badge" style="background:#F1F5F9; color:#475569;">
                          Taux : ${st.taux === null ? 'Indisponible' : `${escapeHtml(st.taux)}%`}
                        </span>
                      </div>
                    </div>
                    <div class="admin-student-actions">
                      <button type="button" class="btn-group-view btn-edit-student" data-sid="${st.id}" title="Modifier cet étudiant">
                        ✏️ Modifier
                      </button>
                      <button type="button" class="btn-delete-student" data-sid="${st.id}" aria-label="Supprimer ${escapeHtml(st.prenom)} ${escapeHtml(st.nom)}">
                        Supprimer
                      </button>
                    </div>
                  </div>
                `).join('')}
              </div>`
          }

          <!-- Note technique de synchronisation -->
          <p class="tt-disclaimer" style="margin-top: 24px;">
            ℹ️ Les profils et comptes de connexion sont gérés par le serveur. Les mots de passe existants ne sont jamais affichés.
          </p>

        </main>
      </div>
    `;

    bindEvents();
  }

  function bindEvents() {
    document.getElementById('studentCreateAccount')?.addEventListener('change', event => {
      const passwordGroup = document.getElementById('studentInitialPasswordGroup');
      const passwordInput = document.getElementById('inputStudentPassword');
      if (passwordGroup) passwordGroup.hidden = !event.target.checked;
      if (passwordInput) {
        passwordInput.required = event.target.checked;
        if (!event.target.checked) passwordInput.value = '';
      }
    });

    // Retour vers l'accueil administration
    document.getElementById('btnBackToAdminDash')?.addEventListener('click', () => {
      AppState.editingStudentAdmin = null;
      AppState.navigate('admin-dashboard');
    });

    // Barre de recherche
    const searchInput = document.getElementById('searchStudentInput');
    if (searchInput) {
      searchInput.addEventListener('input', (e) => {
        searchTerm = e.target.value;
        mount();
        // Conserver le focus après re-render
        const inp = document.getElementById('searchStudentInput');
        if (inp) {
          inp.focus();
          inp.setSelectionRange(inp.value.length, inp.value.length);
        }
      });
    }

    // Réinitialiser la recherche
    document.getElementById('btnClearSearch')?.addEventListener('click', () => {
      searchTerm = '';
      mount();
    });
    document.getElementById('btnResetSearch')?.addEventListener('click', () => {
      searchTerm = '';
      mount();
    });

    // Basculer l'affichage du formulaire Nouveau
    document.getElementById('btnToggleAddForm')?.addEventListener('click', () => {
      if (isFormOpen) {
        isFormOpen = false;
        editingStudent = null;
        AppState.editingStudentAdmin = null;
      } else {
        isFormOpen = true;
        editingStudent = null;
        AppState.editingStudentAdmin = null;
      }
      mount();
    });

    // Fermer le formulaire
    document.getElementById('btnCloseStudentForm')?.addEventListener('click', () => {
      isFormOpen = false;
      editingStudent = null;
      AppState.editingStudentAdmin = null;
      mount();
    });

    // Boutons "Modifier" sur chaque étudiant
    document.querySelectorAll('.btn-edit-student').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const sid = Number(btn.dataset.sid);
        const found = data.students.find(s => s.id === sid);
        if (found) {
          editingStudent = found;
          AppState.editingStudentAdmin = found;
          isFormOpen = true;
          mount();
          document.getElementById('studentFormWrapper')?.scrollIntoView({ behavior: 'smooth' });
        }
      });
    });

    document.querySelectorAll('.btn-delete-student').forEach(button => {
      button.addEventListener('click', async () => {
        const student = data.students.find(item => String(item.id) === button.dataset.sid);
        if (!student || !confirm(`Supprimer le profil de ${student.prenom} ${student.nom} ?`)) return;
        button.disabled = true;
        try {
          await deleteAdminStudent(student.id);
          data = await fetchAdminData();
          mount(`Profil de ${student.prenom} ${student.nom} supprimé.`);
        } catch (error) {
          showError(error.status === 409
            ? 'Suppression refusée : des absences ou justificatifs sont liés à cet étudiant.'
            : error.message || 'Impossible de supprimer cet étudiant.');
          button.disabled = false;
        }
      });
    });

    // Soumission du formulaire (Ajout ou Modification)
    const form = document.getElementById('studentAdminForm');
    form?.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearAlerts();

      const nom = document.getElementById('inputStudentNom')?.value;
      const prenom = document.getElementById('inputStudentPrenom')?.value;
      const email = document.getElementById('inputStudentEmail')?.value;
      const numeroEtudiant = document.getElementById('inputStudentNum')?.value;
      const groupeId = document.getElementById('selectStudentGroup')?.value;
      const createAccount = document.getElementById('studentCreateAccount')?.checked ?? false;
      const initialPassword = document.getElementById('inputStudentPassword')?.value || '';
      const newPassword = editingStudent?.userId ? initialPassword : '';

      if (!nom || !prenom || !email || !numeroEtudiant || !groupeId) {
        showError('Veuillez renseigner tous les champs obligatoires.');
        return;
      }
      if (createAccount && !editingStudent?.userId && initialPassword.length < 8) {
        showError('Le mot de passe initial doit comporter au moins 8 caractères.');
        return;
      }
      if (newPassword && newPassword.length < 8) {
        showError('Le nouveau mot de passe doit comporter au moins 8 caractères.');
        return;
      }

      try {
        const wasEditing = Boolean(editingStudent);
        const accountEmailChanged = editingStudent?.userId
          && email.trim().toLowerCase() !== editingStudent.email?.trim().toLowerCase();
        if (editingStudent?.userId && (accountEmailChanged || newPassword)) {
          await updateAdminAccount(editingStudent.userId, {
            ...(accountEmailChanged ? { email: email.trim().toLowerCase() } : {}),
            ...(newPassword ? { newPassword } : {}),
          });
        }
        const savedStudent = await saveAdminStudent(editingStudent?.id, {
          nom,
          prenom,
          email,
          numeroEtudiant,
          groupeId,
        }, data.groups, editingStudent);
        if (!editingStudent?.userId && createAccount) {
          try {
            await createAdminAccount('STUDENT', savedStudent.id, initialPassword);
          } catch (accountError) {
            data = await fetchAdminData();
            editingStudent = data.students.find(student => Number(student.id) === Number(savedStudent.id)) || savedStudent;
            AppState.editingStudentAdmin = editingStudent;
            isFormOpen = true;
            mount();
            showError(`Profil enregistré, mais compte non créé : ${accountError.message}. Corrigez les informations nécessaires et soumettez à nouveau.`);
            return;
          }
        }
        data = await fetchAdminData();
        editingStudent = null;
        AppState.editingStudentAdmin = null;
        isFormOpen = false;
        mount();
        showSuccess(`Étudiant ${savedStudent.prenom} ${savedStudent.nom} ${wasEditing ? 'modifié' : 'ajouté'}.`);
      } catch (error) {
        showError(error.status === 409
          ? 'Cet email ou numéro étudiant est déjà utilisé.'
          : error.message || 'Impossible d’enregistrer cet étudiant.');
      }
    });
  }

  function showSuccess(msg) {
    const el = document.getElementById('adminStudentAlertSuccess');
    const txt = document.getElementById('adminStudentAlertSuccessText');
    if (el && txt) {
      txt.textContent = msg;
      el.hidden = false;
      el.style.display = 'flex';
      setTimeout(() => {
        el.hidden = true;
        el.style.display = 'none';
      }, 4500);
    }
  }

  function showError(msg) {
    const el = document.getElementById('adminStudentAlertError');
    const txt = document.getElementById('adminStudentAlertErrorText');
    if (el && txt) {
      txt.textContent = msg;
      el.hidden = false;
      el.style.display = 'flex';
      setTimeout(() => {
        el.hidden = true;
        el.style.display = 'none';
      }, 5000);
    }
  }

  function clearAlerts() {
    const s = document.getElementById('adminStudentAlertSuccess');
    const e = document.getElementById('adminStudentAlertError');
    if (s) { s.hidden = true; s.style.display = 'none'; }
    if (e) { e.hidden = true; e.style.display = 'none'; }
  }

  mount();
}
