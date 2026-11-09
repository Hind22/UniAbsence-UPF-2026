/**
 * ⚠️ DONNÉES FICTIVES COMMUNES (2026–2027)
 * Partagées entre l'espace Étudiant et l'espace Enseignant.
 * Endpoint futur Spring Boot :
 *   - GET /api/v1/students/{id}/absences
 *   - GET /api/v1/teachers/me/groups
 *   - GET /api/v1/groups/{id}/students
 */

export const MOCK_STUDENT = {
  id: 1,
  nom: 'Benali',
  prenom: 'Ahmed',
  name: 'Ahmed Benali',
  email: 'a.benali@univ-alger.dz',
  filiere: 'Informatique – L3',
  groupeId: 'G1',
  groupeNom: 'Groupe 1 (L3 Info)',
  annee: '2026-2027',
  avatar: 'AB',
};

// 8 absences initiales d'Ahmed Benali : 3 justifiées + 5 non justifiées
export const MOCK_ABSENCES_STUDENT_1 = [
  {
    id: 1,
    studentId: 1,
    date: '2026-10-28',
    matiere: 'Algorithmique avancée',
    enseignant: 'Dr. Kaddour',
    seance: 'CM 08h00–10h00',
    typeSeance: 'CM',
    heureDebut: '08:00',
    heureFin: '10:00',
    statut: 'unjustified',
    justificatif: null,
    justificatifStatut: null,
  },
  {
    id: 2,
    studentId: 1,
    date: '2026-10-22',
    matiere: 'Réseaux informatiques',
    enseignant: 'Mme. Haddad',
    seance: 'TD 10h00–12h00',
    typeSeance: 'TD',
    heureDebut: '10:00',
    heureFin: '12:00',
    statut: 'justified',
    justificatif: 'certificat_medical.pdf',
    justificatifStatut: 'accepted',
  },
  {
    id: 3,
    studentId: 1,
    date: '2026-10-20',
    matiere: 'Bases de données',
    enseignant: 'M. Rahmani',
    seance: 'TP 14h00–16h00',
    typeSeance: 'TP',
    heureDebut: '14:00',
    heureFin: '16:00',
    statut: 'unjustified',
    justificatif: null,
    justificatifStatut: null,
  },
  {
    id: 4,
    studentId: 1,
    date: '2026-10-15',
    matiere: 'Génie logiciel',
    enseignant: 'Dr. Meziane',
    seance: 'CM 08h00–10h00',
    typeSeance: 'CM',
    heureDebut: '08:00',
    heureFin: '10:00',
    statut: 'justified',
    justificatif: 'ordonnance.pdf',
    justificatifStatut: 'accepted',
  },
  {
    id: 5,
    studentId: 1,
    date: '2026-10-10',
    matiere: 'Algorithmique avancée',
    enseignant: 'Dr. Kaddour',
    seance: 'TD 13h00–15h00',
    typeSeance: 'TD',
    heureDebut: '13:00',
    heureFin: '15:00',
    statut: 'unjustified',
    justificatif: null,
    justificatifStatut: null,
  },
  {
    id: 6,
    studentId: 1,
    date: '2026-10-07',
    matiere: "Systèmes d'exploitation",
    enseignant: 'M. Bouzidi',
    seance: 'TP 16h00–18h00',
    typeSeance: 'TP',
    heureDebut: '16:00',
    heureFin: '18:00',
    statut: 'justified',
    justificatif: 'justificatif_transport.pdf',
    justificatifStatut: 'accepted',
  },
  {
    id: 7,
    studentId: 1,
    date: '2026-10-03',
    matiere: 'Bases de données',
    enseignant: 'M. Rahmani',
    seance: 'CM 10h00–12h00',
    typeSeance: 'CM',
    heureDebut: '10:00',
    heureFin: '12:00',
    statut: 'unjustified',
    justificatif: null,
    justificatifStatut: null,
  },
  {
    id: 8,
    studentId: 1,
    date: '2026-09-29',
    matiere: 'Réseaux informatiques',
    enseignant: 'Mme. Haddad',
    seance: 'CM 08h00–10h00',
    typeSeance: 'CM',
    heureDebut: '08:00',
    heureFin: '10:00',
    statut: 'unjustified',
    justificatif: null,
    justificatifStatut: null,
  },
];

// ── Nombre de séances prévues dans le semestre (donnée fictive explicite) ──
export const TOTAL_PLANNED_SESSIONS = 30;

export const MOCK_ABSENCES = MOCK_ABSENCES_STUDENT_1;

export const MOCK_STATS = {
  total: 8,
  justified: 3,
  unjustified: 5,
};

// ── Liste initiale des enseignants existants dans le projet ───────────────
export const MOCK_TEACHERS = [
  {
    id: 101,
    identifiantEnseignant: 'ENS-2026-0101',
    nom: 'Kaddour',
    prenom: 'Mohamed',
    name: 'Dr. Kaddour Mohamed',
    grade: 'Maître de Conférences',
    matiere: 'Algorithmique avancée',
    email: 'm.kaddour@univ-alger.dz',
    groupes: ['G1', 'G2', 'M1-GL'],
    avatar: 'MK',
    departement: 'Informatique',
    annee: '2026-2027',
  },
  {
    id: 102,
    identifiantEnseignant: 'ENS-2026-0102',
    nom: 'Haddad',
    prenom: 'Leila',
    name: 'Mme. Haddad Leila',
    grade: 'Maître de Conférences B',
    matiere: 'Réseaux informatiques',
    email: 'l.haddad@univ-alger.dz',
    groupes: ['G1', 'G2'],
    avatar: 'LH',
    departement: 'Informatique',
    annee: '2026-2027',
  },
  {
    id: 103,
    identifiantEnseignant: 'ENS-2026-0103',
    nom: 'Rahmani',
    prenom: 'Karim',
    name: 'M. Rahmani Karim',
    grade: 'Maître Assistant',
    matiere: 'Bases de données',
    email: 'k.rahmani@univ-alger.dz',
    groupes: ['G1'],
    avatar: 'KR',
    departement: 'Informatique',
    annee: '2026-2027',
  },
];

// Enseignant par défaut pour la session démo
export const MOCK_TEACHER = MOCK_TEACHERS[0];

export const MOCK_GROUPS = [
  {
    id: 'G1',
    nom: 'Groupe 1 (L3 Info)',
    specialite: 'Informatique – L3',
    effectif: 5,
    matiere: 'Algorithmique avancée',
  },
  {
    id: 'G2',
    nom: 'Groupe 2 (L3 Info)',
    specialite: 'Informatique – L3',
    effectif: 4,
    matiere: 'Algorithmique avancée',
  },
  {
    id: 'M1-GL',
    nom: 'Groupe Master 1 GL',
    specialite: 'Génie Logiciel – M1',
    effectif: 3,
    matiere: 'Complexité & Algorithmes',
  },
];

// ── Ensemble des étudiants répartis par groupes ───────────────────────────
// Ahmed Benali (ID 1) est dans le Groupe 1 avec ses 8 absences (3J, 5NJ)
export const MOCK_STUDENTS_BY_GROUP = {
  G1: [
    {
      id: 1,
      nom: 'Benali',
      prenom: 'Ahmed',
      email: 'a.benali@univ-alger.dz',
      filiere: 'Informatique – L3',
      absencesTotal: 8,
      absencesJustifiees: 3,
      absencesNonJustifiees: 5,
      absences: MOCK_ABSENCES_STUDENT_1,
      avatar: 'AB',
    },
    {
      id: 2,
      nom: 'Meziane',
      prenom: 'Samia',
      email: 's.meziane@univ-alger.dz',
      filiere: 'Informatique – L3',
      absencesTotal: 2,
      absencesJustifiees: 2,
      absencesNonJustifiees: 0,
      absences: [
        {
          id: 201,
          date: '2026-10-14',
          matiere: 'Algorithmique avancée',
          enseignant: 'Dr. Kaddour',
          seance: 'TD 13h00–15h00',
          typeSeance: 'TD',
          heureDebut: '13:00',
          heureFin: '15:00',
          statut: 'justified',
          justificatif: 'certificat_medical.pdf',
          justificatifStatut: 'accepted',
        },
        {
          id: 202,
          date: '2026-10-07',
          matiere: 'Algorithmique avancée',
          enseignant: 'Dr. Kaddour',
          seance: 'CM 08h00–10h00',
          typeSeance: 'CM',
          heureDebut: '08:00',
          heureFin: '10:00',
          statut: 'justified',
          justificatif: 'arret_maladie.pdf',
          justificatifStatut: 'accepted',
        },
      ],
      avatar: 'SM',
    },
    {
      id: 3,
      nom: 'Cherif',
      prenom: 'Yacine',
      email: 'y.cherif@univ-alger.dz',
      filiere: 'Informatique – L3',
      absencesTotal: 4,
      absencesJustifiees: 1,
      absencesNonJustifiees: 3,
      absences: [
        {
          id: 301,
          date: '2026-10-21',
          matiere: 'Algorithmique avancée',
          enseignant: 'Dr. Kaddour',
          seance: 'CM 08h00–10h00',
          typeSeance: 'CM',
          heureDebut: '08:00',
          heureFin: '10:00',
          statut: 'unjustified',
          justificatif: null,
          justificatifStatut: null,
        },
        {
          id: 302,
          date: '2026-10-14',
          matiere: 'Algorithmique avancée',
          enseignant: 'Dr. Kaddour',
          seance: 'TD 13h00–15h00',
          typeSeance: 'TD',
          heureDebut: '13:00',
          heureFin: '15:00',
          statut: 'unjustified',
          justificatif: null,
          justificatifStatut: null,
        },
      ],
      avatar: 'YC',
    },
    {
      id: 4,
      nom: 'Boussaid',
      prenom: 'Fatima',
      email: 'f.boussaid@univ-alger.dz',
      filiere: 'Informatique – L3',
      absencesTotal: 0,
      absencesJustifiees: 0,
      absencesNonJustifiees: 0,
      absences: [],
      avatar: 'FB',
    },
    {
      id: 5,
      nom: 'Hamidi',
      prenom: 'Karim',
      email: 'k.hamidi@univ-alger.dz',
      filiere: 'Informatique – L3',
      absencesTotal: 6,
      absencesJustifiees: 2,
      absencesNonJustifiees: 4,
      absences: [
        {
          id: 501,
          date: '2026-10-28',
          matiere: 'Algorithmique avancée',
          enseignant: 'Dr. Kaddour',
          seance: 'CM 08h00–10h00',
          typeSeance: 'CM',
          heureDebut: '08:00',
          heureFin: '10:00',
          statut: 'unjustified',
          justificatif: null,
          justificatifStatut: null,
        },
      ],
      avatar: 'KH',
    },
  ],
  G2: [
    {
      id: 6,
      nom: 'Larbi',
      prenom: 'Amine',
      email: 'a.larbi@univ-alger.dz',
      filiere: 'Informatique – L3',
      absencesTotal: 1,
      absencesJustifiees: 1,
      absencesNonJustifiees: 0,
      absences: [],
      avatar: 'AL',
    },
    {
      id: 7,
      nom: 'Mansouri',
      prenom: 'Khadidja',
      email: 'k.mansouri@univ-alger.dz',
      filiere: 'Informatique – L3',
      absencesTotal: 3,
      absencesJustifiees: 0,
      absencesNonJustifiees: 3,
      absences: [],
      avatar: 'KM',
    },
    {
      id: 8,
      nom: 'Saadi',
      prenom: 'Walid',
      email: 'w.saadi@univ-alger.dz',
      filiere: 'Informatique – L3',
      absencesTotal: 0,
      absencesJustifiees: 0,
      absencesNonJustifiees: 0,
      absences: [],
      avatar: 'WS',
    },
    {
      id: 9,
      nom: 'Taleb',
      prenom: 'Nour',
      email: 'n.taleb@univ-alger.dz',
      filiere: 'Informatique – L3',
      absencesTotal: 2,
      absencesJustifiees: 1,
      absencesNonJustifiees: 1,
      absences: [],
      avatar: 'NT',
    },
  ],
  'M1-GL': [
    {
      id: 10,
      nom: 'Zitouni',
      prenom: 'Sofiane',
      email: 's.zitouni@univ-alger.dz',
      filiere: 'Génie Logiciel – M1',
      absencesTotal: 1,
      absencesJustifiees: 1,
      absencesNonJustifiees: 0,
      absences: [],
      avatar: 'SZ',
    },
    {
      id: 11,
      nom: 'Belkacem',
      prenom: 'Ines',
      email: 'i.belkacem@univ-alger.dz',
      filiere: 'Génie Logiciel – M1',
      absencesTotal: 2,
      absencesJustifiees: 0,
      absencesNonJustifiees: 2,
      absences: [],
      avatar: 'IB',
    },
    {
      id: 12,
      nom: 'Dahmani',
      prenom: 'Bilel',
      email: 'b.dahmani@univ-alger.dz',
      filiere: 'Génie Logiciel – M1',
      absencesTotal: 0,
      absencesJustifiees: 0,
      absencesNonJustifiees: 0,
      absences: [],
      avatar: 'BD',
    },
  ],
};

// ── Administrateur Démo (2026–2027) ─────────────────────────────────────────
export const MOCK_ADMIN = {
  id: 999,
  name: 'Mme. Mansouri Leila',
  nom: 'Mansouri',
  prenom: 'Leila',
  role: 'Administrateur',
  fonction: 'Responsable de la scolarité & absences',
  email: 'scolarite@univ-alger.dz',
  avatar: 'LM',
  annee: '2026-2027',
  etablissement: 'Faculté des Sciences – Dép. Informatique',
};

/**
 * Simule les appels API futurs pour l'administrateur.
 * Redirige vers fetchAdminDataLive pour la synchronisation dynamique avec localStorage.
 */
export async function fetchAdminData() {
  const { fetchAdminDataLive } = await import('./storage.js');
  return fetchAdminDataLive();
}

/**
 * Simule les appels API futurs pour l'étudiant.
 * Redirige vers fetchStudentDataLive pour la synchronisation dynamique avec localStorage.
 */
export async function fetchStudentData() {
  const { fetchStudentDataLive } = await import('./storage.js');
  return fetchStudentDataLive();
}

/**
 * Simule les appels API futurs pour l'enseignant.
 * Redirige vers fetchTeacherDataLive pour la synchronisation dynamique avec localStorage.
 */
export async function fetchTeacherData() {
  const { fetchTeacherDataLive } = await import('./storage.js');
  return fetchTeacherDataLive();
}
