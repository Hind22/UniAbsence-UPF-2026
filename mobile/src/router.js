/**
 * Router SPA minimal.
 * Gère la navigation entre les pages sans rechargement.
 * Futur : intégrer l'authentification JWT depuis Spring Boot.
 */

// État global de l'application
export const AppState = {
  currentPage: 'login',          // page courante
  currentUser: null,             // Utilisateur authentifié rempli après connexion
  studentData: null,             // Données serveur de l'espace étudiant pendant la session
  teacherData: null,             // Données serveur de l'espace enseignant pendant la session
  selectedAbsence: null,         // Absence sélectionnée pour la page détail
  targetAbsenceForUpload: null,  // Absence ciblée pour ouverture directe du formulaire de dépôt
  selectedStudent: null,         // Étudiant sélectionné par l'enseignant pour consultation
  activeTeacherGroup: 'G1',      // Groupe actif dans l'espace enseignant
  editingAbsence: null,          // Absence en cours de modification par l'enseignant
  editingStudentAdmin: null,     // Étudiant en cours de modification dans l'espace administration
  editingTeacherAdmin: null,     // Enseignant en cours de modification dans l'espace administration
  navigate(page, user = null) {
    if (user) this.currentUser = user;
    this.currentPage = page;
    renderPage(page);
  },
};

// Registre des pages
const pages = {};

export function registerPage(name, renderFn) {
  pages[name] = renderFn;
}

function renderPage(name) {
  const app = document.querySelector('#app');
  app.innerHTML = '';           // vide le contenu
  app.className = `page-${name}`;
  if (pages[name]) {
    pages[name](app, AppState);
  } else {
    app.innerHTML = `<p style="color:red;">Page "${name}" introuvable.</p>`;
  }
}

export function startApp() {
  renderPage(AppState.currentPage);
}
