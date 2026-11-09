/**
 * Page de gestion des groupes de l'espace Administration.
 */
import { AppState } from '../router.js';
import {
  deleteAdminGroup,
  fetchAdminData,
  requireAdmin,
  saveAdminGroup,
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

export async function renderAdminGroups(container) {
  container.innerHTML = '<div class="loading-screen"><div class="loading-spinner"></div><p>Chargement des groupes…</p></div>';
  let data;
  try {
    requireAdmin();
    data = await fetchAdminData();
  } catch (error) {
    container.innerHTML = `
      <div class="dashboard-shell"><header class="topbar"><button class="topbar-back" id="adminLoadBack">‹</button><span class="topbar-title">Gestion des groupes</span></header>
        <main class="dash-content"><div class="form-alert" role="alert">${escapeHtml(error.message)}</div><button class="btn-login" id="adminLoadRetry">Réessayer</button></main>
      </div>`;
    document.getElementById('adminLoadBack')?.addEventListener('click', () => AppState.navigate('admin-dashboard'));
    document.getElementById('adminLoadRetry')?.addEventListener('click', () => renderAdminGroups(container));
    return;
  }
  let editingGroup = null;
  let isFormOpen = false;

  function mount() {
    const groups = data.groups;
    const students = data.students;

    const availableStudents = students.filter(student =>
      !editingGroup || student.groupeId !== editingGroup.id
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
            <span class="topbar-title">Gestion des groupes</span>
          </div>
          <span class="topbar-sub">${groups.length} groupe${groups.length > 1 ? 's' : ''}</span>
        </header>

        <main class="dash-content">
          <div id="adminGroupAlertSuccess" class="form-alert-success" hidden style="display:none;">
            <span id="adminGroupAlertSuccessText"></span>
          </div>
          <div id="adminGroupAlertError" class="form-alert" hidden style="display:none; margin-bottom:12px;">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            <span id="adminGroupAlertErrorText"></span>
          </div>

          <div class="admin-group-toolbar">
            <div>
              <h2 class="admin-group-heading">Groupes pédagogiques</h2>
              <p class="admin-group-intro">Effectifs, enseignants et affectations des étudiants</p>
            </div>
            <button type="button" class="btn-add-student" id="btnToggleGroupForm">
              ${isFormOpen ? '✕ Fermer' : '➕ Nouveau groupe'}
            </button>
          </div>

          <section id="groupFormWrapper" class="upload-modal group-form"
                   style="${isFormOpen ? 'display:block;' : 'display:none;'}">
            <div class="upload-header">
              <h3>${editingGroup ? `Modifier : ${escapeHtml(editingGroup.id)}` : 'Créer un groupe'}</h3>
              <button type="button" class="btn-close-modal" id="btnCloseGroupForm" aria-label="Fermer le formulaire">✕</button>
            </div>
            <form id="adminGroupForm" class="login-form" novalidate>
              <div class="group-form-fields">
                <div class="form-group">
                  <label for="inputGroupId">Identifiant du groupe *</label>
                  <input type="text" id="inputGroupId" class="input-basic"
                         placeholder="ex. L2-INFO-A" value="${escapeHtml(editingGroup?.id || '')}"
                         ${editingGroup ? 'readonly aria-describedby="groupIdHelp"' : 'required'} />
                  ${editingGroup ? '<small id="groupIdHelp">L’identifiant est conservé pour préserver les affectations existantes.</small>' : ''}
                </div>
                <div class="form-group">
                  <label for="inputGroupName">Nom du groupe *</label>
                  <input type="text" id="inputGroupName" class="input-basic"
                         placeholder="ex. Groupe 1 (L3 Info)" value="${escapeHtml(editingGroup?.nom || '')}" required />
                </div>
                <div class="form-group">
                  <label for="inputGroupSpecialty">Spécialité / niveau *</label>
                  <input type="text" id="inputGroupSpecialty" class="input-basic"
                         placeholder="ex. Informatique – L3" value="${escapeHtml(editingGroup?.specialite || '')}" required />
                </div>
                <div class="form-group">
                  <label for="inputGroupSubject">Matière principale *</label>
                  <input type="text" id="inputGroupSubject" class="input-basic"
                         placeholder="ex. Algorithmique avancée" value="${escapeHtml(editingGroup?.matiere || '')}" required />
                </div>
              </div>

              <fieldset class="group-assignment-field">
                <legend>Affecter / réaffecter des étudiants</legend>
                ${availableStudents.length
                  ? `<p class="group-assignment-help">Les étudiants sélectionnés rejoindront ce groupe; leurs identifiants et absences seront conservés.</p>
                     <div class="group-student-options">
                       ${availableStudents.map(student => `
                         <label class="group-student-option">
                           <input type="checkbox" name="groupStudents" value="${escapeHtml(student.id)}" />
                           <span><strong>${escapeHtml(student.prenom)} ${escapeHtml(student.nom)}</strong>
                             <small>${escapeHtml(student.numeroEtudiant)} · Groupe actuel : ${escapeHtml(student.groupeId)}</small>
                           </span>
                         </label>
                       `).join('')}
                     </div>`
                  : '<p class="group-assignment-help">Tous les étudiants sont déjà affectés à ce groupe.</p>'
                }
              </fieldset>
              <button type="submit" class="btn-login" id="btnSaveGroupSubmit">
                ${editingGroup ? '💾 Enregistrer les modifications' : '➕ Créer le groupe'}
              </button>
            </form>
          </section>

          <div class="admin-list-header">
            <span class="alh-count">${groups.length} groupe${groups.length > 1 ? 's' : ''} au total</span>
            <span class="alh-sub">${students.filter(student => student.groupeId).length} étudiants affectés</span>
          </div>

          ${groups.length
            ? `<div class="admin-groups-list">
                ${groups.map(group => `
                  <article class="admin-group-card">
                    <div class="admin-group-card-header">
                      <div class="admin-group-card-title">
                        <span class="admin-group-id">${escapeHtml(group.id)}</span>
                        <div>
                          <h3>${escapeHtml(group.nom)}</h3>
                          <p>${escapeHtml(group.specialite)} · ${escapeHtml(group.matiere)}</p>
                        </div>
                      </div>
                      <button type="button" class="btn-edit-student btn-edit-group"
                              data-gid="${escapeHtml(group.id)}" aria-label="Modifier le groupe ${escapeHtml(group.id)}">
                        ✏️ Modifier
                      </button>
                      <button type="button" class="btn-edit-student btn-delete-group"
                              data-gid="${escapeHtml(group.id)}" aria-label="Supprimer le groupe ${escapeHtml(group.id)}">
                        Supprimer
                      </button>
                    </div>
                    <div class="admin-group-metrics">
                      <span class="group-metric"><strong>${group.effectif}</strong> étudiant${group.effectif > 1 ? 's' : ''}</span>
                      <span class="group-metric"><strong>${group.enseignants.length}</strong> enseignant${group.enseignants.length > 1 ? 's' : ''}</span>
                    </div>
                    <div class="admin-group-relations">
                      <section>
                        <h4>Enseignants affectés</h4>
                        ${group.enseignants.length
                          ? `<div class="group-relation-tags">${group.enseignants.map(teacher => `
                              <span class="group-relation-tag">${escapeHtml(`${teacher.prenom || ''} ${teacher.nom || ''}`.trim() || teacher.name || teacher.identifiantEnseignant)}</span>
                            `).join('')}</div>`
                          : '<p class="group-relation-empty">Aucun enseignant affecté.</p>'
                        }
                      </section>
                      <section>
                        <h4>Étudiants affectés</h4>
                        ${group.etudiants.length
                          ? `<div class="group-relation-tags">${group.etudiants.map(student => `
                              <span class="group-relation-tag">${escapeHtml(`${student.prenom || ''} ${student.nom || ''}`.trim())}</span>
                            `).join('')}</div>`
                          : '<p class="group-relation-empty">Aucun étudiant affecté.</p>'
                        }
                      </section>
                    </div>
                  </article>
                `).join('')}
              </div>`
            : '<div class="empty-state"><p>Aucun groupe enregistré.</p></div>'
          }

          <p class="tt-disclaimer">
            ℹ️ Les groupes, affectations et effectifs affichés proviennent du serveur.
          </p>
        </main>
      </div>
    `;
    bindEvents();
  }

  function bindBackButton() {
    document.getElementById('btnBackToAdminDash')?.addEventListener('click', () => {
      AppState.navigate('admin-dashboard');
    });
  }

  function bindEvents() {
    bindBackButton();

    document.getElementById('btnToggleGroupForm')?.addEventListener('click', () => {
      isFormOpen = !isFormOpen;
      editingGroup = null;
      mount();
    });
    document.getElementById('btnCloseGroupForm')?.addEventListener('click', () => {
      isFormOpen = false;
      editingGroup = null;
      mount();
    });
    document.querySelectorAll('.btn-edit-group').forEach(button => {
      button.addEventListener('click', () => {
        editingGroup = data.groups.find(group => group.id === button.dataset.gid) || null;
        if (!editingGroup) {
          showError('Groupe introuvable.');
          return;
        }
        isFormOpen = true;
        mount();
        document.getElementById('groupFormWrapper')?.scrollIntoView({ behavior: 'smooth' });
      });
    });

    document.querySelectorAll('.btn-delete-group').forEach(button => {
      button.addEventListener('click', async () => {
        const group = data.groups.find(item => item.id === button.dataset.gid);
        if (!group || !confirm(`Supprimer le groupe ${group.id} ?`)) return;
        button.disabled = true;
        try {
          await deleteAdminGroup(group.id);
          data = await fetchAdminData();
          mount();
          showSuccess(`Groupe ${group.id} supprimé.`);
        } catch (error) {
          showError(error.status === 409
            ? 'Suppression refusée : ce groupe contient encore des étudiants ou des enseignants affectés.'
            : error.message || 'Impossible de supprimer ce groupe.');
          button.disabled = false;
        }
      });
    });

    document.getElementById('adminGroupForm')?.addEventListener('submit', async event => {
      event.preventDefault();
      clearAlerts();
      const groupData = {
        id: document.getElementById('inputGroupId')?.value || '',
        nom: document.getElementById('inputGroupName')?.value || '',
        specialite: document.getElementById('inputGroupSpecialty')?.value || '',
        matiere: document.getElementById('inputGroupSubject')?.value || '',
      };
      const studentIds = Array.from(document.querySelectorAll('input[name="groupStudents"]:checked'))
        .map(input => input.value);

      try {
        const wasEditing = Boolean(editingGroup);
        const result = await saveAdminGroup(editingGroup?.id, groupData, studentIds, data.students, editingGroup);
        data = await fetchAdminData();
        editingGroup = null;
        isFormOpen = false;
        mount();
        showSuccess(`Groupe ${result.id} ${wasEditing ? 'modifié' : 'créé'}.`);
      } catch (error) {
        try {
          data = await fetchAdminData();
          mount();
        } catch (reloadError) {
          console.error('Impossible de recharger les données après l’échec d’une affectation.', reloadError);
        }
        showError(error.status === 409
          ? 'Cet identifiant existe déjà ou une relation empêche la modification.'
          : `Échec de l’enregistrement : ${error.message}`);
      }
    });
  }

  function showSuccess(message) {
    const alert = document.getElementById('adminGroupAlertSuccess');
    const text = document.getElementById('adminGroupAlertSuccessText');
    if (alert && text) {
      text.textContent = message;
      alert.hidden = false;
      alert.style.display = 'flex';
    }
  }

  function showError(message) {
    const alert = document.getElementById('adminGroupAlertError');
    const text = document.getElementById('adminGroupAlertErrorText');
    if (alert && text) {
      text.textContent = message;
      alert.hidden = false;
      alert.style.display = 'flex';
    }
  }

  function clearAlerts() {
    ['adminGroupAlertSuccess', 'adminGroupAlertError'].forEach(id => {
      const alert = document.getElementById(id);
      if (alert) {
        alert.hidden = true;
        alert.style.display = 'none';
      }
    });
  }

  mount();
}
