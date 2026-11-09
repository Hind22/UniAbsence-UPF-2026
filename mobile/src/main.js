/**
 * main.js – Point d'entrée de l'application UniAbsence
 * Architecture : SPA minimaliste (routeur maison)
 */
import './style.css';
import { registerPage, startApp } from './router.js';
import { renderLogin } from './pages/login.js';
import { renderStudentDashboard } from './pages/student-dashboard.js';
import { renderTimetable } from './pages/timetable.js';
import { renderAbsenceDetails } from './pages/absence-details.js';
import { renderTeacherDashboard } from './pages/teacher-dashboard.js';
import { renderTeacherStudents } from './pages/teacher-students.js';
import { renderTeacherManageAbsences } from './pages/teacher-manage-absences.js';
import { renderAdminDashboard } from './pages/admin-dashboard.js';
import { renderAdminStudents } from './pages/admin-students.js';
import { renderAdminTeachers } from './pages/admin-teachers.js';
import { renderAdminGroups } from './pages/admin-groups.js';
import { renderAdminAbsenceStats } from './pages/admin-absence-stats.js';

const previewMedia = window.matchMedia('(min-width: 700px) and (hover: hover) and (pointer: fine)');
const previewToggle = document.querySelector('#preview-toggle');
let previewWasManuallyChanged = false;

function setIphonePreview(enabled) {
  document.body.classList.toggle('iphone-preview', enabled);
  if (previewToggle) {
    previewToggle.setAttribute('aria-pressed', String(enabled));
    previewToggle.setAttribute(
      'aria-label',
      enabled ? 'Désactiver la prévisualisation iPhone' : 'Activer la prévisualisation iPhone',
    );
    previewToggle.textContent = enabled
      ? 'Désactiver la prévisualisation iPhone'
      : 'Activer la prévisualisation iPhone';
  }
}

setIphonePreview(previewMedia.matches);
previewToggle?.addEventListener('click', () => {
  previewWasManuallyChanged = true;
  setIphonePreview(!document.body.classList.contains('iphone-preview'));
});
previewMedia.addEventListener('change', event => {
  if (!event.matches) previewWasManuallyChanged = false;
  if (!previewWasManuallyChanged) setIphonePreview(event.matches);
});

const previewClock = document.querySelector('#preview-clock');
function updatePreviewClock() {
  if (previewClock) {
    previewClock.textContent = new Intl.DateTimeFormat('fr-FR', {
      hour: 'numeric',
      minute: '2-digit',
    }).format(new Date());
  }
}
updatePreviewClock();
window.setInterval(updatePreviewClock, 30_000);

// Enregistrement des pages SPA
registerPage('login',                   renderLogin);
registerPage('student-dashboard',       renderStudentDashboard);
registerPage('timetable',               renderTimetable);
registerPage('absence-details',         renderAbsenceDetails);
registerPage('teacher-dashboard',       renderTeacherDashboard);
registerPage('teacher-students',        renderTeacherStudents);
registerPage('teacher-manage-absences', renderTeacherManageAbsences);
registerPage('admin-dashboard',         renderAdminDashboard);
registerPage('admin-students',          renderAdminStudents);
registerPage('admin-teachers',          renderAdminTeachers);
registerPage('admin-groups',            renderAdminGroups);
registerPage('admin-absence-stats',     renderAdminAbsenceStats);

// Démarrage de l'application
startApp();
