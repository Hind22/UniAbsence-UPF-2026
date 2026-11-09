/**
 * Page Emploi du temps – UniAbsence.
 * Les séances sont chargées depuis l'API du profil étudiant connecté.
 */
import { AppState } from '../router.js';
import { clearAccessToken } from '../api/client.js';
import { fetchStudentTimetable } from '../api/student.js';

const DAYS = [
  { value: 1, label: 'Lun', full: 'Lundi' },
  { value: 2, label: 'Mar', full: 'Mardi' },
  { value: 3, label: 'Mer', full: 'Mercredi' },
  { value: 4, label: 'Jeu', full: 'Jeudi' },
  { value: 5, label: 'Ven', full: 'Vendredi' },
  { value: 6, label: 'Sam', full: 'Samedi' },
];

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  })[character]);
}

function currentDayOfWeek() {
  const day = new Date().getDay();
  return day === 0 ? 1 : day;
}

export async function renderTimetable(container) {
  if (AppState.currentUser?.role !== 'student') {
    clearAccessToken();
    AppState.navigate('login');
    return;
  }

  container.innerHTML = `
    <div class="loading-screen">
      <div class="loading-spinner"></div>
      <p>Chargement de votre emploi du temps…</p>
    </div>
  `;

  let timetable;
  try {
    timetable = await fetchStudentTimetable();
  } catch (error) {
    container.innerHTML = `
      <div class="dashboard-shell">
        <header class="topbar">
          <div class="topbar-left">
            <button class="topbar-back" id="backBtn" aria-label="Retour au tableau de bord">‹</button>
            <span class="topbar-title">Emploi du temps</span>
          </div>
        </header>
        <main class="dash-content">
          <div class="form-alert" role="alert">${escapeHtml(error.message || 'Impossible de charger l’emploi du temps.')}</div>
          <button class="btn-login" id="retryTimetable">Réessayer</button>
        </main>
      </div>
    `;
    document.getElementById('backBtn')?.addEventListener('click', () => {
      AppState.navigate('student-dashboard');
    });
    document.getElementById('retryTimetable')?.addEventListener('click', () => {
      renderTimetable(container);
    });
    return;
  }

  let activeDay = currentDayOfWeek();

  function mount() {
    const sessionsForDay = timetable.sessions.filter(session => session.dayOfWeek === activeDay);
    const activeDayName = DAYS.find(day => day.value === activeDay)?.full || 'Lundi';

    container.innerHTML = `
      <div class="dashboard-shell">
        <header class="topbar">
          <div class="topbar-left">
            <button class="topbar-back" id="backBtn" aria-label="Retour au tableau de bord">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="2.5" width="20" height="20">
                <polyline points="15 18 9 12 15 6"/>
              </svg>
            </button>
            <span class="topbar-title">Emploi du temps</span>
          </div>
          <span class="topbar-sub">${escapeHtml(timetable.groupId || 'Sans groupe')}</span>
        </header>

        <div class="day-selector-wrap">
          <div class="day-selector" role="tablist" aria-label="Jours de la semaine">
            ${DAYS.map(day => {
              const count = timetable.sessions.filter(session => session.dayOfWeek === day.value).length;
              return `
                <button class="day-btn ${day.value === activeDay ? 'day-active' : ''}"
                        type="button" role="tab" aria-selected="${day.value === activeDay}"
                        data-day="${day.value}" aria-label="${day.full}, ${count} séance${count > 1 ? 's' : ''}">
                  ${day.label}
                </button>
              `;
            }).join('')}
          </div>
        </div>

        <main class="dash-content">
          <div class="tt-day-header">
            <h2 class="tt-day-name">${activeDayName}</h2>
            <span class="tt-day-count">${sessionsForDay.length} séance${sessionsForDay.length > 1 ? 's' : ''}</span>
          </div>

          ${timetable.demonstration
            ? `<div class="info-banner" role="note">
                 <span>Emploi du temps de démonstration — année ${escapeHtml(timetable.academicYear || '2026-2027')}. Ces horaires ne représentent pas un planning universitaire officiel.</span>
               </div>`
            : ''}

          ${!timetable.groupId
            ? `<div class="empty-state"><p>Aucun groupe n’est associé à votre profil étudiant.</p></div>`
            : sessionsForDay.length
              ? `<div class="tt-list">
                  ${sessionsForDay.map(session => `
                    <article class="tt-card">
                      <div class="tt-time-col">
                        <span>${escapeHtml(session.startTime)}</span>
                        <span class="tt-vsep"></span>
                        <span>${escapeHtml(session.endTime)}</span>
                      </div>
                      <div class="tt-info">
                        <h3 class="tt-matiere">${escapeHtml(session.subject)}</h3>
                        <div class="tt-meta">
                          <span>${escapeHtml(session.dayName || activeDayName)}</span>
                          <span>${escapeHtml(session.teacherName || 'Enseignant non renseigné')}</span>
                          <span>${escapeHtml(session.room || 'Salle non renseignée')}</span>
                        </div>
                      </div>
                      <span class="tt-type-badge type-${escapeHtml(session.sessionType)}">${escapeHtml(session.sessionType)}</span>
                    </article>
                  `).join('')}
                </div>`
              : `<div class="empty-state"><p>Aucune séance prévue pour ${activeDayName} dans les données disponibles.</p></div>`
          }

          <p class="tt-disclaimer">Groupe : ${escapeHtml(timetable.groupName || 'Non renseigné')} · Année universitaire : ${escapeHtml(timetable.academicYear || 'Non renseignée')}</p>
        </main>
      </div>
    `;

    document.getElementById('backBtn')?.addEventListener('click', () => {
      AppState.navigate('student-dashboard');
    });
    document.querySelectorAll('[data-day]').forEach(button => {
      button.addEventListener('click', () => {
        activeDay = Number(button.dataset.day);
        mount();
      });
    });
  }

  mount();
}
