/**
 * Page de connexion – UniAbsence
 */
import { ApiError, login as authenticate } from '../api/client.js';
import { AppState } from '../router.js';

const ROLES = [
  { value: 'student',       label: 'Étudiant' },
  { value: 'teacher',       label: 'Enseignant' },
  { value: 'administrator', label: 'Administrateur' },
];

export function renderLogin(container) {
  container.innerHTML = `
    <div class="login-wrapper">
      <div class="login-card">

        <!-- En-tête -->
        <div class="login-header">
          <div class="logo-circle">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M22 10v6M2 10l10-5 10 5-10 5z"/>
              <path d="M6 12v5c3 3 9 3 12 0v-5"/>
            </svg>
          </div>
          <h1 class="app-name">UniAbsence</h1>
          <p class="app-tagline">Gestion des absences universitaires</p>
        </div>

        <!-- Formulaire -->
        <form id="loginForm" class="login-form" novalidate>

          <div class="form-group">
            <label for="role">Connexion en tant que</label>
            <div class="select-wrapper">
              <select id="role" name="role" required>
                ${ROLES.map(r => `<option value="${r.value}">${r.label}</option>`).join('')}
              </select>
              <svg class="select-arrow" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"
                   fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="6 9 12 15 18 9"/>
              </svg>
            </div>
          </div>

          <div class="form-group">
            <label for="email">Adresse e-mail</label>
            <div class="input-wrapper">
              <svg class="input-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"
                   fill="none" stroke="currentColor" stroke-width="2">
                <rect x="2" y="4" width="20" height="16" rx="2"/>
                <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/>
              </svg>
              <input type="email" id="email" name="email"
                     placeholder="exemple@universite.dz" required autocomplete="email"/>
            </div>
            <span class="field-error" id="emailError"></span>
          </div>

          <div class="form-group">
            <label for="password">Mot de passe</label>
            <div class="input-wrapper">
              <svg class="input-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"
                   fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
              </svg>
              <input type="password" id="password" name="password"
                     placeholder="••••••••" required autocomplete="current-password"/>
              <button type="button" id="togglePwd" class="toggle-pwd" aria-label="Afficher le mot de passe">
                <svg id="eyeIcon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"
                     fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                  <circle cx="12" cy="12" r="3"/>
                </svg>
              </button>
            </div>
            <span class="field-error" id="passwordError"></span>
          </div>

          <div class="forgot-row">
            <a href="#" class="forgot-link">Mot de passe oublié ?</a>
          </div>

          <button type="submit" class="btn-login" id="loginBtn">
            <span class="btn-text">Se connecter</span>
            <span class="btn-spinner" id="loginSpinner" hidden></span>
          </button>

          <div class="form-alert" id="formAlert" hidden>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            <span id="formAlertMsg">Email ou mot de passe incorrect.</span>
          </div>

        </form>

        <p class="login-footer">
          &copy; ${new Date().getFullYear()} UniAbsence &mdash; Tous droits réservés
        </p>
      </div>
    </div>
  `;

  // ── Logique ──────────────────────────────────────────────────────────────
  const form           = document.getElementById('loginForm');
  const emailInput     = document.getElementById('email');
  const passwordInput  = document.getElementById('password');
  const emailError     = document.getElementById('emailError');
  const passwordError  = document.getElementById('passwordError');
  const formAlert      = document.getElementById('formAlert');
  const formAlertMsg   = document.getElementById('formAlertMsg');
  const loginBtn       = document.getElementById('loginBtn');
  const loginSpinner   = document.getElementById('loginSpinner');
  const togglePwd      = document.getElementById('togglePwd');
  const eyeIcon        = document.getElementById('eyeIcon');

  if (togglePwd && passwordInput && eyeIcon) {
    togglePwd.addEventListener('click', () => {
      const hidden = passwordInput.type === 'password';
      passwordInput.type = hidden ? 'text' : 'password';
      eyeIcon.innerHTML = hidden
        ? `<path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/>`
        : `<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>`;
    });
  }

  if (emailInput) {
    emailInput.addEventListener('input', () => {
      emailInput.classList.remove('input-error');
      if (emailError) emailError.textContent = '';
    });
  }
  if (passwordInput) {
    passwordInput.addEventListener('input', () => {
      passwordInput.classList.remove('input-error');
      if (passwordError) passwordError.textContent = '';
    });
  }

  if (form) {
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      let valid = true;

      if (emailError) emailError.textContent = '';
      if (passwordError) passwordError.textContent = '';
      if (formAlert) formAlert.hidden = true;

      const emailVal = emailInput ? emailInput.value.trim() : '';
      if (!emailVal) {
        if (emailError) emailError.textContent = 'Veuillez saisir votre adresse e-mail.';
        emailInput?.classList.add('input-error');
        valid = false;
      } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailVal)) {
        if (emailError) emailError.textContent = 'Adresse e-mail invalide.';
        emailInput?.classList.add('input-error');
        valid = false;
      } else {
        emailInput?.classList.remove('input-error');
      }

      const pwdVal = passwordInput ? passwordInput.value : '';
      if (!pwdVal) {
        if (passwordError) passwordError.textContent = 'Veuillez saisir votre mot de passe.';
        passwordInput?.classList.add('input-error');
        valid = false;
      } else if (pwdVal.length < 6) {
        if (passwordError) passwordError.textContent = 'Le mot de passe doit contenir au moins 6 caractères.';
        passwordInput?.classList.add('input-error');
        valid = false;
      } else {
        passwordInput?.classList.remove('input-error');
      }

      if (!valid) return;

      if (loginBtn) loginBtn.disabled = true;
      if (loginSpinner) loginSpinner.hidden = false;
      const btnText = loginBtn?.querySelector('.btn-text');
      if (btnText) btnText.textContent = 'Connexion…';
      try {
        const session = await authenticate(emailVal, pwdVal);
        const destinations = {
          STUDENT: { page: 'student-dashboard', role: 'student' },
          TEACHER: { page: 'teacher-dashboard', role: 'teacher' },
          ADMIN: { page: 'admin-dashboard', role: 'administrator' },
        };
        const destination = destinations[session.role];

        AppState.navigate(destination.page, {
          id: session.user.id,
          studentId: session.user.studentId,
          teacherId: session.user.teacherId,
          role: destination.role,
          email: session.user.email,
          name: session.user.name || session.user.email,
        });
      } catch (error) {
        if (formAlertMsg) {
          formAlertMsg.textContent = error instanceof ApiError
            ? (error.status === 401
              ? 'Adresse e-mail ou mot de passe incorrect.'
              : error.message)
            : 'Une erreur inattendue est survenue pendant la connexion.';
        }
        if (!(error instanceof ApiError)) {
          console.error('Échec inattendu de la connexion.', error);
        }
        if (formAlert) formAlert.hidden = false;
      } finally {
        if (loginBtn) loginBtn.disabled = false;
        if (loginSpinner) loginSpinner.hidden = true;
        if (btnText) btnText.textContent = 'Se connecter';
      }
    });
  }
}
