/**
 * Page Détails de l'Absence – UniAbsence
 * Permet de consulter l'ensemble des détails d'une absence sélectionnée
 * et propose l'accès au dépôt de justificatif si non justifiée.
 */
import { AppState } from '../router.js';

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  })[character]);
}

function formatDate(iso) {
  if (!iso) return 'Date non renseignée';
  return new Date(iso).toLocaleDateString('fr-FR', {
    weekday: 'long', day: '2-digit', month: 'long', year: 'numeric',
  });
}

function renderBadge(statut) {
  if (statut === 'justified') {
    return `<span class="badge badge-ok">✅ Absence Justifiée</span>`;
  }
  return `<span class="badge badge-err">❌ Absence Non Justifiée</span>`;
}

function renderDocStatus(status) {
  switch (status) {
    case 'accepted':
      return `<span class="badge badge-doc-accepted">🟢 Justificatif Accepté</span>`;
    case 'rejected':
      return `<span class="badge badge-doc-rejected">🔴 Justificatif Refusé</span>`;
    case 'pending':
    default:
      return `<span class="badge badge-doc-pending">🟡 Justificatif En attente de validation</span>`;
  }
}

export function renderAbsenceDetails(container) {
  const absence = AppState.selectedAbsence;

  // Si aucune absence n'est sélectionnée, rediriger vers le dashboard
  if (!absence) {
    AppState.navigate('student-dashboard');
    return;
  }

  // Chercher le justificatif dans les données serveur chargées pour la session.
  const studentData = AppState.studentData;
  const justification = studentData?.justifications?.find(
    doc => String(doc.absenceId) === String(absence.id)
  );

  // Déterminer les infos du justificatif
  const hasJustificatif = Boolean(absence.justificatif || justification);
  const fileName = justification?.fileName || absence.justificatif || null;
  const docStatus = justification?.status || absence.justificatifStatut || 'pending';
  const uploadDate = justification?.uploadDate || (hasJustificatif ? 'Enregistré' : null);
  const sessionType = ['CM', 'TD', 'TP'].includes(absence.typeSeance)
    ? absence.typeSeance
    : 'CM';

  container.innerHTML = `
    <div class="dashboard-shell">
      <!-- Topbar -->
      <header class="topbar">
        <div class="topbar-left">
          <button class="topbar-back" id="btnBackToAbsences" aria-label="Retour aux absences">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2.5" width="20" height="20">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
          </button>
          <span class="topbar-title">Détails de l'absence</span>
        </div>
        <span class="topbar-sub">#${absence.id}</span>
      </header>

      <!-- Contenu -->
      <main class="dash-content">
        <!-- En-tête de carte -->
        <div class="detail-header-card">
          <div class="dh-type-wrap">
            <span class="tt-type-badge type-${sessionType}">${sessionType}</span>
            <span class="dh-date-sub">${new Date(absence.date).toLocaleDateString('fr-FR')}</span>
          </div>
          <h2 class="dh-matiere">${escapeHtml(absence.matiere)}</h2>
          <div class="dh-status-wrap">
            ${renderBadge(absence.statut)}
          </div>
        </div>

        <!-- Fiche d'informations -->
        <div class="detail-info-card">
          <h3 class="detail-section-title">Informations sur la séance</h3>

          <div class="detail-row">
            <div class="dr-icon">📅</div>
            <div class="dr-content">
              <span class="dr-label">Date de l'absence</span>
              <strong class="dr-val">${escapeHtml(formatDate(absence.date))}</strong>
            </div>
          </div>

          <div class="detail-row">
            <div class="dr-icon">⏰</div>
            <div class="dr-content">
              <span class="dr-label">Horaires</span>
              <strong class="dr-val">${escapeHtml(absence.heureDebut || '08:00')} — ${escapeHtml(absence.heureFin || '10:00')} (${escapeHtml(absence.seance)})</strong>
            </div>
          </div>

          <div class="detail-row">
            <div class="dr-icon">🎓</div>
            <div class="dr-content">
              <span class="dr-label">Type de séance</span>
              <strong class="dr-val">${sessionType === 'CM' ? 'Cours Magistral (CM)' : (sessionType === 'TD' ? 'Travaux Dirigés (TD)' : 'Travaux Pratiques (TP)')}</strong>
            </div>
          </div>

          <div class="detail-row">
            <div class="dr-icon">👨‍🏫</div>
            <div class="dr-content">
              <span class="dr-label">Enseignant responsable</span>
              <strong class="dr-val">${escapeHtml(absence.enseignant)}</strong>
            </div>
          </div>
        </div>

        <!-- Section Justificatif -->
        <div class="detail-info-card" style="margin-top: 14px;">
          <h3 class="detail-section-title">Statut du justificatif</h3>

          ${hasJustificatif
            ? `
              <div class="doc-present-box">
                <div class="dp-header">
                  <span class="dp-file">📎 ${escapeHtml(fileName)}</span>
                  ${renderDocStatus(docStatus)}
                </div>
                ${uploadDate ? `<p class="dp-meta">Déposé le : ${escapeHtml(uploadDate)}</p>` : ''}
                <p class="dp-note">ℹ️ Le serveur conserve les métadonnées du justificatif, pas le fichier.</p>
              </div>
            `
            : `
              <div class="doc-missing-box">
                <p class="dm-text">⚠️ Aucun justificatif n'est encore associé à cette absence.</p>
                ${absence.statut === 'unjustified'
                  ? `
                    <button class="btn-login" id="btnGoUploadDoc" style="margin-top: 12px; font-size: .92rem; padding: 11px 16px;">
                      📁 Déposer un justificatif maintenant
                    </button>
                  `
                  : ''
                }
              </div>
            `
          }
        </div>

        <!-- Bouton retour inférieur -->
        <button class="btn-back-bottom" id="btnBackBottom">
          ← Retour à la liste des absences
        </button>
      </main>
    </div>
  `;

  // Événements
  const goBack = () => {
    AppState.navigate('student-dashboard');
  };

  document.getElementById('btnBackToAbsences')?.addEventListener('click', goBack);
  document.getElementById('btnBackBottom')?.addEventListener('click', goBack);

  const btnGoUploadDoc = document.getElementById('btnGoUploadDoc');
  if (btnGoUploadDoc) {
    btnGoUploadDoc.addEventListener('click', () => {
      // Redirige vers l'onglet justificatifs
      AppState.targetAbsenceForUpload = absence;
      AppState.navigate('student-dashboard');
    });
  }
}
