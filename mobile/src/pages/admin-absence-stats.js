/**
 * Statistiques d'absences de l'espace Administration.
 */
import { AppState } from '../router.js';
import { calculateAdminStatistics, fetchAdminData, requireAdmin } from '../api/admin.js';

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  })[character]);
}

function formatRate(rate) {
  return Number.isFinite(rate) ? `${rate.toFixed(1)} %` : '—';
}

export async function renderAdminAbsenceStats(container) {
  const allGroupsFilter = '__all_groups__';
  let selectedGroupId = allGroupsFilter;
  container.innerHTML = '<div class="loading-screen"><div class="loading-spinner"></div><p>Chargement des statistiques…</p></div>';
  let data;
  try {
    requireAdmin();
    data = await fetchAdminData();
  } catch (error) {
    container.innerHTML = `
      <div class="dashboard-shell"><header class="topbar"><button class="topbar-back" id="adminLoadBack">‹</button><span class="topbar-title">Statistiques des absences</span></header>
        <main class="dash-content"><div class="form-alert" role="alert">${escapeHtml(error.message)}</div><button class="btn-login" id="adminLoadRetry">Réessayer</button></main>
      </div>`;
    document.getElementById('adminLoadBack')?.addEventListener('click', () => AppState.navigate('admin-dashboard'));
    document.getElementById('adminLoadRetry')?.addEventListener('click', () => renderAdminAbsenceStats(container));
    return;
  }

  function mount() {
    const groups = data.groups;
    const statistics = calculateAdminStatistics(data, selectedGroupId);

    const maxAbsences = Math.max(1, ...statistics.groups.map(group => group.totalAbsences));
    const selectedGroupLabel = selectedGroupId === allGroupsFilter
      ? 'Tous les groupes'
      : groups.find(group => group.id === selectedGroupId)?.nom || selectedGroupId;

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
            <span class="topbar-title">Statistiques des absences</span>
          </div>
          <button type="button" class="stats-refresh-button" id="btnRefreshAbsenceStats" aria-label="Actualiser les statistiques">
            Actualiser
          </button>
        </header>

        <main class="dash-content">
          <section class="absence-stats-intro">
            <div>
              <h2>Vue d’ensemble</h2>
              <p>Indicateurs calculés à partir des données enregistrées dans la base.</p>
            </div>
            <label class="stats-group-filter" for="selectStatsGroup">
              <span>Groupe</span>
              <select id="selectStatsGroup">
                <option value="${allGroupsFilter}" ${selectedGroupId === allGroupsFilter ? 'selected' : ''}>Tous les groupes</option>
                ${groups.map(group => `
                  <option value="${escapeHtml(group.id)}" ${selectedGroupId === group.id ? 'selected' : ''}>
                    ${escapeHtml(group.id)} — ${escapeHtml(group.nom)}
                  </option>
                `).join('')}
              </select>
            </label>
          </section>

          <p class="stats-current-scope">Périmètre : <strong>${escapeHtml(selectedGroupLabel)}</strong></p>

          <section class="absence-stats-cards" aria-label="Indicateurs des absences">
            <article class="absence-stat-card stat-students">
              <span class="absence-stat-label">${selectedGroupId === allGroupsFilter ? 'Étudiants inscrits' : 'Étudiants du groupe'}</span>
              <strong class="absence-stat-value" id="statTotalStudents">${statistics.studentCount}</strong>
            </article>
            <article class="absence-stat-card stat-absences">
              <span class="absence-stat-label">Absences enregistrées</span>
              <strong class="absence-stat-value" id="statTotalAbsences">${statistics.totalAbsences}</strong>
            </article>
            <article class="absence-stat-card stat-justified">
              <span class="absence-stat-label">Absences justifiées</span>
              <strong class="absence-stat-value" id="statJustifiedAbsences">${statistics.justified}</strong>
            </article>
            <article class="absence-stat-card stat-unjustified">
              <span class="absence-stat-label">Absences non justifiées</span>
              <strong class="absence-stat-value" id="statUnjustifiedAbsences">${statistics.unjustified}</strong>
            </article>
            <article class="absence-stat-card stat-pending">
              <span class="absence-stat-label">Justificatifs en attente</span>
              <strong class="absence-stat-value" id="statPendingDocuments">${statistics.pendingDocuments}</strong>
            </article>
            <article class="absence-stat-card stat-rate">
              <span class="absence-stat-label">Taux des séances prévues manquées</span>
              <strong class="absence-stat-value" id="statAbsenceRate">${formatRate(statistics.absenceRate)}</strong>
            </article>
          </section>

          <section class="absence-chart-section">
            <div class="absence-section-heading">
              <div>
                <h3>Comparaison des absences par groupe</h3>
                <p>Chaque barre représente le nombre d’absences enregistrées.</p>
              </div>
              <div class="absence-chart-legend" aria-label="Légende">
                <span><i class="legend-swatch justified-swatch"></i>Justifiées</span>
                <span><i class="legend-swatch unjustified-swatch"></i>Non justifiées</span>
              </div>
            </div>
            ${statistics.groups.length
              ? `<div class="absence-chart" role="img" aria-label="Graphique comparant les absences justifiées et non justifiées par groupe">
                  ${statistics.groups.map(group => {
                    const totalWidth = group.totalAbsences
                      ? (group.totalAbsences / maxAbsences) * 100
                      : 0;
                    const justifiedWidth = group.totalAbsences
                      ? (group.justified / group.totalAbsences) * 100
                      : 0;
                    return `
                      <div class="absence-chart-row">
                        <span class="absence-chart-label">${escapeHtml(group.id)}</span>
                        <div class="absence-chart-track" aria-label="${escapeHtml(group.nom)} : ${group.totalAbsences} absences">
                          <div class="absence-chart-bar" style="width:${totalWidth}%;">
                            <span class="chart-segment justified-segment" style="width:${justifiedWidth}%;"></span>
                            <span class="chart-segment unjustified-segment" style="width:${100 - justifiedWidth}%;"></span>
                          </div>
                        </div>
                        <strong class="absence-chart-value">${group.totalAbsences}</strong>
                      </div>
                    `;
                  }).join('')}
                </div>`
              : '<p class="stats-empty-state">Aucun groupe enregistré.</p>'
            }
          </section>

          <section class="absence-group-table-section">
            <div class="absence-section-heading">
              <div>
                <h3>Statistiques par groupe</h3>
                <p>Un groupe sans absence conserve un total de zéro.</p>
              </div>
            </div>
            ${statistics.groups.length
              ? `<div class="absence-table-scroll">
                  <table class="absence-stats-table">
                    <thead>
                      <tr>
                        <th scope="col">Groupe</th>
                        <th scope="col">Étudiants</th>
                        <th scope="col">Total</th>
                        <th scope="col">Justifiées</th>
                        <th scope="col">Non justifiées</th>
                        <th scope="col">Taux d’absence</th>
                      </tr>
                    </thead>
                    <tbody>
                      ${statistics.groups.map(group => `
                        <tr>
                          <th scope="row">${escapeHtml(group.id)}<small>${escapeHtml(group.nom)}</small></th>
                          <td>${group.studentCount}</td>
                          <td><strong>${group.totalAbsences}</strong></td>
                          <td><span class="stats-number-pill justified-pill">${group.justified}</span></td>
                          <td><span class="stats-number-pill unjustified-pill">${group.unjustified}</span></td>
                          <td>${formatRate(group.absenceRate)}</td>
                        </tr>
                      `).join('')}
                    </tbody>
                  </table>
                </div>`
              : '<p class="stats-empty-state">Aucune donnée par groupe à afficher.</p>'
            }
          </section>

          <aside class="absence-rate-note">
            <strong>Disponibilité du taux d’absence</strong>
            <p>
              Le taux ne peut pas être calculé : la base ne contient pas le nombre de séances prévues par étudiant ni par groupe.
              Aucun dénominateur simulé n’est utilisé. Dès qu’un calendrier réel sera disponible, la formule sera
              <em>absences enregistrées ÷ séances prévues × 100</em>.
            </p>
            <p>Les statuts <code>justified</code> et justificatifs <code>accepted</code> sont comptés comme justifiés; les autres statuts enregistrés comme non justifiés, conformément aux règles actuelles de l’application. Les justificatifs sont comptés séparément des absences.</p>
          </aside>
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
    document.getElementById('selectStatsGroup')?.addEventListener('change', event => {
      selectedGroupId = event.target.value;
      mount();
    });
    document.getElementById('btnRefreshAbsenceStats')?.addEventListener('click', async event => {
      event.currentTarget.disabled = true;
      try {
        data = await fetchAdminData();
        mount();
      } catch (error) {
        const alert = document.createElement('p');
        alert.className = 'form-alert';
        alert.setAttribute('role', 'alert');
        alert.textContent = error.message;
        container.querySelector('.dash-content')?.prepend(alert);
      }
    });
  }

  mount();
}
