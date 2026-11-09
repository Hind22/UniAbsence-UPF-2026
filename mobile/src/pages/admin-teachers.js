/**
 * Page de gestion des enseignants de l'espace Administration.
 */
import { AppState } from '../router.js';
import {
  createAdminAccount,
  deleteAdminTeacher,
  fetchAdminData,
  requireAdmin,
  saveAdminTeacher,
  updateAdminAccount,
} from '../api/admin.js';

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  })[character]);
}

export async function renderAdminTeachers(container) {
  container.innerHTML = '<div class="loading-screen"><div class="loading-spinner"></div><p>Chargement des enseignants…</p></div>';
  let data;
  try {
    requireAdmin();
    data = await fetchAdminData();
  } catch (error) {
    container.innerHTML = `
      <div class="dashboard-shell"><header class="topbar"><button class="topbar-back" id="adminLoadBack">‹</button><span class="topbar-title">Gestion des enseignants</span></header>
        <main class="dash-content"><div class="form-alert" role="alert">${escapeHtml(error.message)}</div><button class="btn-login" id="adminLoadRetry">Réessayer</button></main>
      </div>`;
    document.getElementById('adminLoadBack')?.addEventListener('click', () => AppState.navigate('admin-dashboard'));
    document.getElementById('adminLoadRetry')?.addEventListener('click', () => renderAdminTeachers(container));
    return;
  }
  let searchTerm = '';
  let editingTeacher = AppState.editingTeacherAdmin || null;
  let isFormOpen = Boolean(editingTeacher);

  function mount() {
    const allTeachers = data.teachers;
    const groups = data.groups;

    const query = searchTerm.trim().toLowerCase();
    const filteredTeachers = allTeachers.filter(teacher =>
      `${teacher.nom || ''} ${teacher.prenom || ''}`.toLowerCase().includes(query) ||
      `${teacher.prenom || ''} ${teacher.nom || ''}`.toLowerCase().includes(query) ||
      String(teacher.identifiantEnseignant || '').toLowerCase().includes(query)
    );

    container.innerHTML = `
      <div class="dashboard-shell">
        <header class="topbar">
          <div class="topbar-left">
            <button class="topbar-back" id="btnBackToAdminDash" aria-label="Retour au tableau de bord administration">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="2.5" width="20" height="20">
                <polyline points="15 18 9 12 15 6"/>
              </svg>
            </button>
            <span class="topbar-title">Gestion des enseignants</span>
          </div>
          <span class="topbar-sub">${allTeachers.length} enseignant${allTeachers.length > 1 ? 's' : ''}</span>
        </header>

        <main class="dash-content">
          <div id="adminTeacherAlertSuccess" class="form-alert-success" hidden style="display:none;">
            <span id="adminTeacherAlertSuccessText"></span>
          </div>
          <div id="adminTeacherAlertError" class="form-alert" hidden style="display:none; margin-bottom:12px;">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            <span id="adminTeacherAlertErrorText"></span>
          </div>

          <div class="admin-search-bar-wrap">
            <div class="admin-search-input-box">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="2" width="18" height="18">
                <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <input type="search" id="searchTeacherInput" aria-label="Rechercher un enseignant"
                     placeholder="Rechercher par nom ou identifiant enseignant…"
                     value="${escapeHtml(searchTerm)}" />
              ${searchTerm ? '<button type="button" id="btnClearTeacherSearch" class="btn-clear-search" aria-label="Effacer la recherche">✕</button>' : ''}
            </div>
            <button type="button" class="btn-add-student" id="btnToggleTeacherForm">
              ${isFormOpen ? '✕ Fermer' : '➕ Nouveau'}
            </button>
          </div>

          <section id="teacherFormWrapper" class="upload-modal teacher-form"
                   style="${isFormOpen ? 'display:block;' : 'display:none;'}">
            <div class="upload-header">
              <h3>${editingTeacher
                ? `Modifier : ${escapeHtml(editingTeacher.prenom)} ${escapeHtml(editingTeacher.nom)}`
                : 'Ajouter un enseignant'}</h3>
              <button type="button" class="btn-close-modal" id="btnCloseTeacherForm" aria-label="Fermer le formulaire">✕</button>
            </div>
            <form id="teacherAdminForm" class="login-form" novalidate>
              <div class="teacher-form-fields">
                <div class="form-group">
                  <label for="inputTeacherNom">Nom *</label>
                  <input type="text" id="inputTeacherNom" class="input-basic" autocomplete="family-name"
                         value="${escapeHtml(editingTeacher?.nom || '')}" required />
                </div>
                <div class="form-group">
                  <label for="inputTeacherPrenom">Prénom *</label>
                  <input type="text" id="inputTeacherPrenom" class="input-basic" autocomplete="given-name"
                         value="${escapeHtml(editingTeacher?.prenom || '')}" required />
                </div>
                <div class="form-group">
                  <label for="inputTeacherEmail">Email professionnel *</label>
                  <input type="email" id="inputTeacherEmail" class="input-basic" autocomplete="email"
                         placeholder="nom@univ-alger.dz" value="${escapeHtml(editingTeacher?.email || '')}" required />
                </div>
                <div class="form-group">
                  <label for="inputTeacherIdentifier">Identifiant enseignant *</label>
                  <input type="text" id="inputTeacherIdentifier" class="input-basic"
                         placeholder="ENS-2026-0104"
                         value="${escapeHtml(editingTeacher?.identifiantEnseignant || '')}" required />
                </div>
                <div class="form-group teacher-subject-field">
                  <label for="inputTeacherSubject">Matière enseignée *</label>
                  <input type="text" id="inputTeacherSubject" class="input-basic"
                         value="${escapeHtml(editingTeacher?.matiere || '')}" required />
                </div>
              </div>

              <fieldset class="teacher-groups-field">
                <legend>Groupe(s) affecté(s) *</legend>
                <div class="teacher-group-options">
                  ${groups.map(group => `
                    <label class="teacher-group-option">
                      <input type="checkbox" name="teacherGroups" value="${escapeHtml(group.id)}"
                        ${editingTeacher?.groupes?.includes(group.id) ? 'checked' : ''} />
                      <span><strong>${escapeHtml(group.id)}</strong> — ${escapeHtml(group.nom)}</span>
                    </label>
                  `).join('')}
                </div>
                <small>Sélectionnez un ou plusieurs groupes.</small>
              </fieldset>
              ${editingTeacher?.userId
                ? `<div class="form-group">
                    <label for="inputTeacherPassword">Réinitialiser le mot de passe du compte (facultatif)</label>
                    <input type="password" id="inputTeacherPassword" class="input-basic"
                           autocomplete="new-password" minlength="8" placeholder="Laisser vide pour ne pas le modifier" />
                  </div>
                  <p class="tt-disclaimer">Compte de connexion associé à ${escapeHtml(editingTeacher.email)}. Aucun mot de passe existant n’est affiché.</p>`
                : `<fieldset class="teacher-groups-field">
                    <label class="teacher-group-option">
                      <input type="checkbox" id="teacherCreateAccount" checked />
                      <span><strong>Créer aussi le compte de connexion</strong></span>
                    </label>
                    <div class="form-group" id="teacherInitialPasswordGroup" style="margin-top:10px;">
                      <label for="inputTeacherPassword">Mot de passe initial *</label>
                      <input type="password" id="inputTeacherPassword" class="input-basic"
                             autocomplete="new-password" minlength="8" required />
                    </div>
                    <small>Le mot de passe est transmis de manière sécurisée et stocké sous forme de hash.</small>
                  </fieldset>`}
              <button type="submit" class="btn-login" id="btnSaveTeacherSubmit">
                ${editingTeacher ? '💾 Enregistrer les modifications' : '➕ Ajouter l’enseignant'}
              </button>
            </form>
          </section>

          <div class="admin-list-header">
            <span class="alh-count">
              ${filteredTeachers.length} enseignant${filteredTeachers.length > 1 ? 's' : ''}
              ${query ? `trouvé${filteredTeachers.length > 1 ? 's' : ''} sur ${allTeachers.length}` : 'au total'}
            </span>
            <span class="alh-sub">Profils synchronisés avec le serveur</span>
          </div>

          ${filteredTeachers.length === 0
            ? `<div class="empty-state">
                 <div class="empty-icon">🔍</div>
                 <p>Aucun enseignant ne correspond à « ${escapeHtml(searchTerm)} ».</p>
                 <button class="link-btn" id="btnResetTeacherSearch">Réinitialiser la recherche</button>
               </div>`
            : `<div class="teacher-list">
                ${filteredTeachers.map(teacher => {
                  const groups = Array.isArray(teacher.groupes) ? teacher.groupes : [];
                  const fullName = `${teacher.prenom || ''} ${teacher.nom || ''}`.trim();
                  const groupLabels = groups.map(groupId => {
                    const group = data.groups.find(item => item.id === groupId);
                    return group ? `${group.id} · ${group.nom}` : groupId;
                  });
                  return `
                    <article class="teacher-item-card">
                      <div class="st-avatar teacher-avatar">${escapeHtml(teacher.avatar || '')}</div>
                      <div class="teacher-info">
                        <div class="teacher-card-heading">
                          <h4 class="st-name">${escapeHtml(fullName)}</h4>
                          <span class="teacher-id-tag">${escapeHtml(teacher.identifiantEnseignant || 'Identifiant manquant')}</span>
                        </div>
                        <p class="st-email">${escapeHtml(teacher.email || '')}</p>
                        <p class="teacher-subject">${escapeHtml(teacher.matiere || 'Matière non renseignée')}</p>
                        <div class="teacher-group-tags">
                          ${groupLabels.map(label => `<span class="st-group-tag">${escapeHtml(label)}</span>`).join('')}
                        </div>
                      </div>
                      <button type="button" class="btn-edit-student btn-edit-teacher"
                              data-tid="${escapeHtml(teacher.id)}" aria-label="Modifier ${escapeHtml(fullName)}">
                        ✏️ Modifier
                      </button>
                      <button type="button" class="btn-edit-student btn-delete-teacher"
                              data-tid="${escapeHtml(teacher.id)}" aria-label="Supprimer ${escapeHtml(fullName)}">
                        Supprimer
                      </button>
                    </article>
                  `;
                }).join('')}
              </div>`
          }

          <p class="tt-disclaimer">
            ℹ️ Les profils, affectations et comptes sont gérés par le serveur. Les mots de passe existants ne sont jamais affichés.
          </p>
        </main>
      </div>
    `;
    bindEvents();
  }

  function bindBackButton() {
    document.getElementById('btnBackToAdminDash')?.addEventListener('click', () => {
      AppState.editingTeacherAdmin = null;
      AppState.navigate('admin-dashboard');
    });
  }

  function bindEvents() {
    bindBackButton();

    document.getElementById('teacherCreateAccount')?.addEventListener('change', event => {
      const passwordGroup = document.getElementById('teacherInitialPasswordGroup');
      const passwordInput = document.getElementById('inputTeacherPassword');
      if (passwordGroup) passwordGroup.hidden = !event.target.checked;
      if (passwordInput) {
        passwordInput.required = event.target.checked;
        if (!event.target.checked) passwordInput.value = '';
      }
    });

    document.getElementById('searchTeacherInput')?.addEventListener('input', event => {
      searchTerm = event.target.value;
      mount();
      const input = document.getElementById('searchTeacherInput');
      input?.focus();
      input?.setSelectionRange(input.value.length, input.value.length);
    });
    document.getElementById('btnClearTeacherSearch')?.addEventListener('click', () => {
      searchTerm = '';
      mount();
    });
    document.getElementById('btnResetTeacherSearch')?.addEventListener('click', () => {
      searchTerm = '';
      mount();
    });

    document.getElementById('btnToggleTeacherForm')?.addEventListener('click', () => {
      isFormOpen = !isFormOpen;
      editingTeacher = null;
      AppState.editingTeacherAdmin = null;
      mount();
    });
    document.getElementById('btnCloseTeacherForm')?.addEventListener('click', () => {
      isFormOpen = false;
      editingTeacher = null;
      AppState.editingTeacherAdmin = null;
      mount();
    });

    document.querySelectorAll('.btn-edit-teacher').forEach(button => {
      button.addEventListener('click', () => {
        editingTeacher = data.teachers.find(teacher => String(teacher.id) === button.dataset.tid) || null;
        if (!editingTeacher) {
          showError('Enseignant introuvable.');
          return;
        }
        AppState.editingTeacherAdmin = editingTeacher;
        isFormOpen = true;
        mount();
        document.getElementById('teacherFormWrapper')?.scrollIntoView({ behavior: 'smooth' });
      });
    });

    document.querySelectorAll('.btn-delete-teacher').forEach(button => {
      button.addEventListener('click', async () => {
        const teacher = data.teachers.find(item => String(item.id) === button.dataset.tid);
        if (!teacher || !confirm(`Supprimer le profil de ${teacher.prenom} ${teacher.nom} ?`)) return;
        button.disabled = true;
        try {
          await deleteAdminTeacher(teacher.id);
          data = await fetchAdminData();
          mount();
          showSuccess(`Profil de ${teacher.prenom} ${teacher.nom} supprimé.`);
        } catch (error) {
          showError(error.status === 409
            ? 'Suppression refusée : des absences ou affectations sont encore liées à cet enseignant.'
            : error.message || 'Impossible de supprimer cet enseignant.');
          button.disabled = false;
        }
      });
    });

    document.getElementById('teacherAdminForm')?.addEventListener('submit', async event => {
      event.preventDefault();
      clearAlerts();
      const teacherData = {
        nom: document.getElementById('inputTeacherNom')?.value || '',
        prenom: document.getElementById('inputTeacherPrenom')?.value || '',
        email: document.getElementById('inputTeacherEmail')?.value || '',
        identifiantEnseignant: document.getElementById('inputTeacherIdentifier')?.value || '',
        matiere: document.getElementById('inputTeacherSubject')?.value || '',
        groupes: Array.from(document.querySelectorAll('input[name="teacherGroups"]:checked'))
          .map(input => input.value),
      };
      const createAccount = document.getElementById('teacherCreateAccount')?.checked ?? false;
      const initialPassword = document.getElementById('inputTeacherPassword')?.value || '';
      const newPassword = editingTeacher?.userId ? initialPassword : '';

      if (createAccount && !editingTeacher?.userId && initialPassword.length < 8) {
        showError('Le mot de passe initial doit comporter au moins 8 caractères.');
        return;
      }
      if (newPassword && newPassword.length < 8) {
        showError('Le nouveau mot de passe doit comporter au moins 8 caractères.');
        return;
      }

      try {
        const wasEditing = Boolean(editingTeacher);
        const accountEmailChanged = editingTeacher?.userId
          && teacherData.email.trim().toLowerCase() !== editingTeacher.email?.trim().toLowerCase();
        if (editingTeacher?.userId && (accountEmailChanged || newPassword)) {
          await updateAdminAccount(editingTeacher.userId, {
            ...(accountEmailChanged ? { email: teacherData.email.trim().toLowerCase() } : {}),
            ...(newPassword ? { newPassword } : {}),
          });
        }
        const savedTeacher = await saveAdminTeacher(editingTeacher?.id, teacherData, editingTeacher);
        if (!editingTeacher?.userId && createAccount) {
          try {
            await createAdminAccount('TEACHER', savedTeacher.id, initialPassword);
          } catch (accountError) {
            data = await fetchAdminData();
            editingTeacher = data.teachers.find(teacher =>
              Number(teacher.id) === Number(savedTeacher.id)
            ) || null;
            AppState.editingTeacherAdmin = editingTeacher;
            isFormOpen = true;
            mount();
            showError(`Profil enregistré, mais compte non créé : ${accountError.message}. Corrigez les informations nécessaires et soumettez à nouveau.`);
            return;
          }
        }
        data = await fetchAdminData();
        editingTeacher = null;
        AppState.editingTeacherAdmin = null;
        isFormOpen = false;
        mount();
        showSuccess(`Enseignant ${teacherData.prenom} ${teacherData.nom} ${wasEditing ? 'modifié' : 'ajouté'}.`);
      } catch (error) {
        showError(error.status === 409
          ? 'Cet email ou identifiant enseignant est déjà utilisé, ou une relation empêche cette modification.'
          : `Échec de l’enregistrement : ${error.message}`);
      }
    });
  }

  function showSuccess(message) {
    const alert = document.getElementById('adminTeacherAlertSuccess');
    const text = document.getElementById('adminTeacherAlertSuccessText');
    if (alert && text) {
      text.textContent = message;
      alert.hidden = false;
      alert.style.display = 'flex';
    }
  }

  function showError(message) {
    const alert = document.getElementById('adminTeacherAlertError');
    const text = document.getElementById('adminTeacherAlertErrorText');
    if (alert && text) {
      text.textContent = message;
      alert.hidden = false;
      alert.style.display = 'flex';
    }
  }

  function clearAlerts() {
    ['adminTeacherAlertSuccess', 'adminTeacherAlertError'].forEach(id => {
      const alert = document.getElementById(id);
      if (alert) {
        alert.hidden = true;
        alert.style.display = 'none';
      }
    });
  }

  mount();
}
