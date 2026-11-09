/**
 * Page Gestion des Absences – Espace Enseignant (TP 3 Agile/Scrum)
 *
 * Fonctionnalités :
 * 1. Enregistrer une absence (Groupe, Étudiant, Date, Matière, Type de séance, Horaires)
 *    avec contrôle strict contre les doublons.
 * 2. Modifier une absence existante avec son identifiant unique.
 * 3. Justifier une absence (Consulter les justificatifs en attente, Accepter ou Refuser).
 *
 * Les lectures et décisions sont effectuées par les API sécurisées du backend.
 */
import { AppState } from '../router.js';
import { clearAccessToken } from '../api/client.js';
import {
  createTeacherAbsence,
  decideTeacherJustification,
  deleteTeacherAbsence,
  downloadTeacherJustification,
  fetchTeacherData,
  justificationHasFile,
  updateTeacherAbsence,
} from '../api/teacher.js';

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  })[character]);
}

export async function renderTeacherManageAbsences(container) {
  container.innerHTML = `
    <div class="loading-screen">
      <div class="loading-spinner"></div>
      <p>Chargement de la gestion des absences…</p>
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
    console.error('Impossible de charger la gestion enseignant.', error);
    container.innerHTML = `
      <div class="loading-screen">
        <p role="alert">Impossible de charger les données : ${escapeHtml(error.message)}</p>
        <button class="btn-login" id="retryTeacherManage">Réessayer</button>
        <button class="btn-back-bottom" id="teacherManageBack">Retour à l’espace enseignant</button>
      </div>
    `;
    document.getElementById('retryTeacherManage')?.addEventListener('click', () => {
      renderTeacherManageAbsences(container);
    });
    document.getElementById('teacherManageBack')?.addEventListener('click', () => {
      AppState.navigate('teacher-dashboard');
    });
    return;
  }
  let { groups, studentsByGroup, absences, justifications } = teacherData;
  AppState.teacherData = teacherData;
  let currentGroup = groups.find(g => g.id === AppState.activeTeacherGroup) || groups[0] || null;
  let editingAbsence = AppState.editingAbsence || null;

  function mount() {
    if (!currentGroup) {
      container.innerHTML = `
        <div class="dashboard-shell">
          <header class="topbar"><button class="topbar-back" id="btnBackToTeacherDash">‹</button><span class="topbar-title">Gestion des absences</span></header>
          <main class="dash-content"><div class="empty-state"><p>Aucun groupe ne vous est actuellement affecté.</p></div></main>
        </div>
      `;
      document.getElementById('btnBackToTeacherDash')?.addEventListener('click', () => {
        AppState.navigate('teacher-dashboard');
      });
      return;
    }
    const allAbs = absences;
    const currentStudents = studentsByGroup[currentGroup.id] || [];
    const studentIds = new Set(currentStudents.map(student => Number(student.id)));
    const pendingDocs = justifications.filter(doc =>
      doc.status === 'pending' && studentIds.has(Number(doc.studentId))
    );

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
            <span class="topbar-title">Gestion des absences</span>
          </div>
          <span class="topbar-sub">${escapeHtml(currentGroup.id)}</span>
        </header>

        <!-- Contenu scrollable -->
        <main class="dash-content">

          <!-- Bannière alertes messages -->
          <div id="actionAlertSuccess" class="form-alert-success" hidden style="display: none;">
            <span id="actionAlertSuccessText">Opération réussie.</span>
          </div>
          <div id="actionAlertError" class="form-alert" hidden style="display: none; margin-bottom: 12px;">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            <span id="actionAlertErrorText">Une erreur est survenue.</span>
          </div>

          <!-- Section 1 : Justificatifs en attente de décision -->
          <section class="section-block" style="margin-bottom: 16px; border-left: 4px solid var(--orange-500);">
            <div class="section-header">
              <h3 class="section-title">Justificatifs à valider</h3>
              <span class="count-badge" style="background:#FEF3C7; color:#92400E;">${pendingDocs.length} en attente</span>
            </div>

            ${pendingDocs.length === 0
              ? `<p style="font-size: .8rem; color: var(--gray-500); padding: 4px 0;">
                   Aucun justificatif en attente pour le moment.
                 </p>`
              : `<div class="pending-docs-list">
                  ${pendingDocs.map(doc => `
                    <div class="pending-doc-box" data-docid="${doc.id}">
                      <div class="pdb-header">
                        <strong class="pdb-matiere">${escapeHtml(doc.matiere)}</strong>
                        <span class="badge badge-doc-pending">🟡 En attente</span>
                      </div>
                      <p class="pdb-meta">📄 <strong>Fichier :</strong> ${escapeHtml(doc.fileName)}</p>
                      <p class="pdb-meta">📅 <strong>Absence du :</strong> ${escapeHtml(doc.absenceDate)} · Déposé le ${escapeHtml(doc.uploadDate)}</p>
                      ${justificationHasFile(doc.filePath)
                        ? `<button type="button" class="btn-decide btn-download-justification" data-docid="${doc.id}"
                                   data-filename="${escapeHtml(doc.fileName)}">📎 Consulter / télécharger le fichier</button>`
                        : '<p class="pdb-meta">Aucun fichier disponible pour ce justificatif.</p>'}
                      <div class="pdb-actions">
                        <button class="btn-decide btn-accept-doc" data-docid="${doc.id}">
                          ✅ Accepter
                        </button>
                        <button class="btn-decide btn-reject-doc" data-docid="${doc.id}">
                          ❌ Refuser
                        </button>
                      </div>
                    </div>
                  `).join('')}
                </div>`
            }
          </section>

          <!-- Section 2 : Formulaire d'Enregistrement / Modification -->
          <section class="section-block" id="formSection" style="margin-bottom: 16px;">
            <div class="section-header">
              <h3 class="section-title" id="formTitle">
                ${editingAbsence ? `Modifier l'absence #${editingAbsence.id}` : 'Enregistrer une absence'}
              </h3>
              ${editingAbsence ? `<button class="link-btn" id="btnCancelEdit" style="color:var(--red-700);">Annuler</button>` : ''}
            </div>

            <form id="absenceForm" class="login-form" novalidate>
              <input type="hidden" id="editAbsenceId" value="${editingAbsence ? editingAbsence.id : ''}" />

              <!-- Choix du groupe -->
              <div class="form-group">
                <label for="selectGroup">Groupe pédagogique</label>
                <div class="select-wrapper">
                  <select id="selectGroup" ${editingAbsence ? 'disabled' : ''}>
                    ${groups.map(g => `
                      <option value="${escapeHtml(g.id)}" ${g.id === currentGroup.id ? 'selected' : ''}>
                      ${escapeHtml(g.nom)} (${escapeHtml(g.specialite)})
                      </option>
                    `).join('')}
                  </select>
                </div>
              </div>

              <!-- Choix de l'étudiant -->
              <div class="form-group">
                <label for="selectStudent">Étudiant concerné</label>
                <div class="select-wrapper">
                  <select id="selectStudent" required ${editingAbsence ? 'disabled' : ''}>
                    ${currentStudents.map(st => `
                      <option value="${st.id}" ${editingAbsence && Number(editingAbsence.studentId) === st.id ? 'selected' : ''}>
                      ${escapeHtml(st.nom)} ${escapeHtml(st.prenom)} (${escapeHtml(st.filiere)})
                      </option>
                    `).join('')}
                  </select>
                </div>
              </div>

              <!-- Date de l'absence -->
              <div class="form-group">
                <label for="inputDate">Date de l'absence</label>
                <input type="date" id="inputDate" class="input-basic" required
                       value="${editingAbsence ? editingAbsence.date : '2026-11-04'}" />
              </div>

              <!-- Matière -->
              <div class="form-group">
                <label for="selectMatiere">Matière</label>
                <div class="select-wrapper">
                  <select id="selectMatiere" required>
                    <option value="Algorithmique avancée" ${editingAbsence?.matiere === 'Algorithmique avancée' ? 'selected' : ''}>Algorithmique avancée</option>
                    <option value="Réseaux informatiques" ${editingAbsence?.matiere === 'Réseaux informatiques' ? 'selected' : ''}>Réseaux informatiques</option>
                    <option value="Bases de données" ${editingAbsence?.matiere === 'Bases de données' ? 'selected' : ''}>Bases de données</option>
                    <option value="Génie logiciel" ${editingAbsence?.matiere === 'Génie logiciel' ? 'selected' : ''}>Génie logiciel</option>
                    <option value="Systèmes d'exploitation" ${editingAbsence?.matiere === "Systèmes d'exploitation" ? 'selected' : ''}>Systèmes d'exploitation</option>
                  </select>
                </div>
              </div>

              <!-- Type de séance -->
              <div class="form-group">
                <label for="selectTypeSeance">Type de séance</label>
                <div class="select-wrapper">
                  <select id="selectTypeSeance" required>
                    <option value="CM" ${editingAbsence?.typeSeance === 'CM' ? 'selected' : ''}>Cours Magistral (CM)</option>
                    <option value="TD" ${editingAbsence?.typeSeance === 'TD' ? 'selected' : ''}>Travaux Dirigés (TD)</option>
                    <option value="TP" ${editingAbsence?.typeSeance === 'TP' ? 'selected' : ''}>Travaux Pratiques (TP)</option>
                  </select>
                </div>
              </div>

              <!-- Horaires début et fin -->
              <div style="display:flex; gap:10px;">
                <div class="form-group" style="flex:1;">
                  <label for="inputHeureDebut">Heure début</label>
                  <input type="time" id="inputHeureDebut" class="input-basic" required
                         value="${editingAbsence ? editingAbsence.heureDebut : '08:00'}" />
                </div>
                <div class="form-group" style="flex:1;">
                  <label for="inputHeureFin">Heure fin</label>
                  <input type="time" id="inputHeureFin" class="input-basic" required
                         value="${editingAbsence ? editingAbsence.heureFin : '10:00'}" />
                </div>
              </div>

              <button type="submit" class="btn-login" style="margin-top: 8px;" id="btnSubmitAbsence" ${currentStudents.length ? '' : 'disabled'}>
                ${editingAbsence ? '💾 Enregistrer les modifications' : '➕ Enregistrer l\'absence'}
              </button>
            </form>
          </section>

          <!-- Section 3 : Historique des absences du groupe avec bouton Modifier -->
          <section class="section-block">
            <div class="section-header">
              <h3 class="section-title">Historique des absences (${currentGroup.nom})</h3>
            </div>

            <div class="absence-history-list">
              ${renderGroupAbsencesHistory(currentGroup, currentStudents, allAbs)}
            </div>
          </section>

        </main>
      </div>
    `;

    bindEvents();
  }

  function renderGroupAbsencesHistory(group, students, allAbs) {
    const studentIds = new Set(students.map(s => Number(s.id)));
    const groupAbs = allAbs.filter(a => studentIds.has(Number(a.studentId)));

    if (groupAbs.length === 0) {
      return `<p style="font-size: .8rem; color: var(--gray-500); padding: 8px 0;">Aucune absence dans ce groupe.</p>`;
    }

    return groupAbs.map(a => {
      const student = students.find(s => s.id === Number(a.studentId)) || { nom: 'Étudiant', prenom: `#${a.studentId}` };
      return `
        <div class="history-absence-card" data-aid="${a.id}">
          <div class="hac-top">
            <div class="hac-info">
              <strong class="hac-student">${escapeHtml(student.nom)} ${escapeHtml(student.prenom)}</strong>
              <div class="hac-sub">
                <span class="hac-subject">${escapeHtml(a.matiere)}</span>
                <span class="badge ${a.typeSeance === 'TP' ? 'type-TP' : (a.typeSeance === 'TD' ? 'type-TD' : 'type-CM')}">${escapeHtml(a.typeSeance || 'CM')}</span>
              </div>
            </div>
            <span class="badge ${a.statut === 'justified' ? 'badge-ok' : 'badge-err'}">
              ${a.statut === 'justified' ? 'Justifiée' : 'Non justifiée'}
            </span>
          </div>

          <div class="hac-bottom">
            <span class="hac-date">📅 ${escapeHtml(new Date(a.date).toLocaleDateString('fr-FR'))} (${escapeHtml(a.heureDebut || '08:00')}–${escapeHtml(a.heureFin || '10:00')})</span>
            <div class="hac-actions">
              <button type="button" class="btn-modify-absence" data-aid="${a.id}">
                ✏️ Modifier
              </button>
              <button type="button" class="btn-delete-absence" data-aid="${a.id}"
                      aria-label="Supprimer l’absence du ${escapeHtml(student.prenom)} ${escapeHtml(student.nom)}">
                Supprimer
              </button>
            </div>
          </div>
        </div>
      `;
    }).join('');
  }

  async function reloadData() {
    teacherData = await fetchTeacherData();
    ({ groups, studentsByGroup, absences, justifications } = teacherData);
    AppState.teacherData = teacherData;
    currentGroup = groups.find(group => group.id === currentGroup?.id) || groups[0] || null;
  }

  function bindEvents() {
    // Bouton retour
    document.getElementById('btnBackToTeacherDash')?.addEventListener('click', () => {
      AppState.editingAbsence = null;
      AppState.navigate('teacher-dashboard');
    });

    // Changement de groupe
    document.getElementById('selectGroup')?.addEventListener('change', (e) => {
      currentGroup = groups.find(g => g.id === e.target.value) || groups[0];
      AppState.activeTeacherGroup = currentGroup.id;
      mount();
    });

    // Annuler la modification
    document.getElementById('btnCancelEdit')?.addEventListener('click', () => {
      AppState.editingAbsence = null;
      editingAbsence = null;
      mount();
    });

    document.querySelectorAll('.btn-download-justification').forEach(button => {
      button.addEventListener('click', async () => {
        button.disabled = true;
        try {
          await downloadTeacherJustification(button.dataset.docid, button.dataset.filename);
        } catch (error) {
          showError(error.message || 'Impossible de télécharger le justificatif.');
        } finally {
          button.disabled = false;
        }
      });
    });

    // Boutons "Modifier" dans la liste
    document.querySelectorAll('.btn-modify-absence').forEach(btn => {
      btn.addEventListener('click', () => {
        const aid = btn.dataset.aid;
        const found = absences.find(a => String(a.id) === String(aid));
        if (found) {
          AppState.editingAbsence = found;
          editingAbsence = found;
          mount();
          document.getElementById('formSection')?.scrollIntoView({ behavior: 'smooth' });
        }
      });
    });

    document.querySelectorAll('.btn-delete-absence').forEach(button => {
      button.addEventListener('click', async () => {
        const absence = absences.find(item => String(item.id) === button.dataset.aid);
        if (!absence || !confirm('Supprimer définitivement cette absence ?')) return;

        button.disabled = true;
        try {
          await deleteTeacherAbsence(absence.id);
          if (String(editingAbsence?.id) === String(absence.id)) {
            AppState.editingAbsence = null;
            editingAbsence = null;
          }
          await reloadData();
          mount();
          showSuccess('Absence supprimée. Les statistiques ont été actualisées.');
        } catch (error) {
          showError(error.message || 'Impossible de supprimer cette absence.');
          button.disabled = false;
        }
      });
    });

    // Décisions sur les justificatifs (Accepter / Refuser avec confirmation)
    document.querySelectorAll('.btn-accept-doc').forEach(btn => {
      btn.addEventListener('click', async () => {
        const docId = btn.dataset.docid;
        if (confirm('Confirmez-vous l\'ACCEPTATION de ce justificatif ? L\'absence associée deviendra justifiée.')) {
          btn.disabled = true;
          try {
            await decideTeacherJustification(docId, 'accepted');
            await reloadData();
            mount();
            showSuccess('Justificatif ACCEPTÉ. L\'absence associée a été mise à jour comme Justifiée.');
          } catch (error) {
            showError(error.message || 'Impossible de traiter ce justificatif.');
            btn.disabled = false;
          }
        }
      });
    });

    document.querySelectorAll('.btn-reject-doc').forEach(btn => {
      btn.addEventListener('click', async () => {
        const docId = btn.dataset.docid;
        if (confirm('Confirmez-vous le REFUS de ce justificatif ? L\'absence associée restera non justifiée.')) {
          btn.disabled = true;
          try {
            await decideTeacherJustification(docId, 'rejected');
            await reloadData();
            mount();
            showSuccess('Justificatif REFUSÉ. L\'absence associée demeure Non justifiée.');
          } catch (error) {
            showError(error.message || 'Impossible de traiter ce justificatif.');
            btn.disabled = false;
          }
        }
      });
    });

    // Soumission du formulaire (Enregistrer OU Modifier)
    const form = document.getElementById('absenceForm');
    form?.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearAlerts();

      const studentId = document.getElementById('selectStudent')?.value;
      const date = document.getElementById('inputDate')?.value;
      const matiere = document.getElementById('selectMatiere')?.value;
      const typeSeance = document.getElementById('selectTypeSeance')?.value;
      const heureDebut = document.getElementById('inputHeureDebut')?.value;
      const heureFin = document.getElementById('inputHeureFin')?.value;

      if (!date || !matiere || !typeSeance || !heureDebut || !heureFin) {
        showError('Veuillez renseigner tous les champs obligatoires.');
        return;
      }

      if (!studentId) {
        showError('Aucun étudiant disponible dans ce groupe.');
        return;
      }

      const submitButton = document.getElementById('btnSubmitAbsence');
      submitButton.disabled = true;
      try {
        const absenceFields = {
          studentId,
          date,
          subject: matiere,
          sessionType: typeSeance,
          startTime: heureDebut,
          endTime: heureFin,
        };
        if (editingAbsence) {
          await updateTeacherAbsence(editingAbsence.id, absenceFields);
          AppState.editingAbsence = null;
          editingAbsence = null;
          await reloadData();
          mount();
          showSuccess('Absence mise à jour avec succès.');
        } else {
          await createTeacherAbsence(absenceFields);
          await reloadData();
          mount();
          showSuccess(`Absence enregistrée avec succès pour l'étudiant sélectionné.`);
        }
      } catch (error) {
        showError(error.message || 'Impossible d’enregistrer cette absence.');
        submitButton.disabled = false;
      }
    });
  }

  function showSuccess(msg) {
    const el = document.getElementById('actionAlertSuccess');
    const txt = document.getElementById('actionAlertSuccessText');
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
    const el = document.getElementById('actionAlertError');
    const txt = document.getElementById('actionAlertErrorText');
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
    const s = document.getElementById('actionAlertSuccess');
    const e = document.getElementById('actionAlertError');
    if (s) {
      s.hidden = true;
      s.style.display = 'none';
    }
    if (e) {
      e.hidden = true;
      e.style.display = 'none';
    }
  }

  mount();
}
