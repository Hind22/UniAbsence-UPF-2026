/**
 * Persistance locale et Service de données unifié – UniAbsence (TP 3 Agile/Scrum)
 *
 * Ce module sert de pont de synchronisation entre l'espace Étudiant, Enseignant et Administration.
 * Dans la version finale, ces fonctions appelleront directement l'API Spring Boot (fetch/axios).
 *
 * En mode démonstration :
 * - Toutes les modifications (absences, décisions, étudiants) sont synchronisées dans localStorage.
 * - Ahmed Benali (ID 1) conserve ses 8 absences (3J, 5NJ) et ses identifiants stables.
 * - Les justificatifs existants sont scrupuleusement préservés.
 */
import {
  MOCK_STUDENT,
  MOCK_TEACHER,
  MOCK_TEACHERS,
  MOCK_ADMIN,
  MOCK_GROUPS,
  MOCK_STUDENTS_BY_GROUP,
  MOCK_ABSENCES_STUDENT_1,
  TOTAL_PLANNED_SESSIONS,
} from './mock.js';

const STORAGE_KEYS = {
  ABSENCES: 'uniabsence_absences_v2',
  DOCS: 'uniabsence_justificatifs_demo',
  STUDENTS: 'uniabsence_students_by_group_v2',
  TEACHERS: 'uniabsence_teachers_v2',
  GROUPS: 'uniabsence_groups_v1',
};

/**
 * Initialise les données si non présentes, en préservant tout justificatif déjà déposé.
 */
function initDataStore() {
  if (typeof localStorage === 'undefined') return;

  if (!localStorage.getItem(STORAGE_KEYS.ABSENCES)) {
    // Créer une liste d'absences globale à partir des étudiants des groupes
    const allInitialAbsences = [...MOCK_ABSENCES_STUDENT_1];
    Object.values(MOCK_STUDENTS_BY_GROUP).forEach(list => {
      list.forEach(st => {
        if (st.id !== 1 && Array.isArray(st.absences)) {
          st.absences.forEach(ab => {
            allInitialAbsences.push({ ...ab, studentId: st.id });
          });
        }
      });
    });
    localStorage.setItem(STORAGE_KEYS.ABSENCES, JSON.stringify(allInitialAbsences));
  }

  // Initialisation de la structure des étudiants avec numéro étudiant par défaut si non présent
  if (!localStorage.getItem(STORAGE_KEYS.STUDENTS)) {
    const initialStudentsWithNum = {};
    Object.keys(MOCK_STUDENTS_BY_GROUP).forEach(gid => {
      initialStudentsWithNum[gid] = MOCK_STUDENTS_BY_GROUP[gid].map(st => ({
        ...st,
        numeroEtudiant: `ETU-2026-${String(st.id).padStart(4, '0')}`,
        groupeId: gid,
      }));
    });
    localStorage.setItem(STORAGE_KEYS.STUDENTS, JSON.stringify(initialStudentsWithNum));
  }

  if (!localStorage.getItem(STORAGE_KEYS.DOCS)) {
    localStorage.setItem(STORAGE_KEYS.DOCS, JSON.stringify([]));
  }

  // Initialisation de la structure des enseignants si non présente
  if (!localStorage.getItem(STORAGE_KEYS.TEACHERS)) {
    localStorage.setItem(STORAGE_KEYS.TEACHERS, JSON.stringify(MOCK_TEACHERS));
  }

  if (localStorage.getItem(STORAGE_KEYS.GROUPS) === null) {
    localStorage.setItem(STORAGE_KEYS.GROUPS, JSON.stringify(MOCK_GROUPS));
  }
}

// Lancer l'initialisation sûre
initDataStore();

/* ==========================================================================
   GESTION DES JUSTIFICATIFS (conservée)
   ========================================================================== */

export function getStoredDocs() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEYS.DOCS) || '[]');
  } catch {
    return [];
  }
}

export function addStoredDoc(entry) {
  const docs = getStoredDocs();
  docs.unshift(entry);
  localStorage.setItem(STORAGE_KEYS.DOCS, JSON.stringify(docs));
  return docs;
}

export function updateDocStatus(docId, newStatus) {
  const docs = getStoredDocs();
  const index = docs.findIndex(d => String(d.id) === String(docId));
  if (index !== -1) {
    docs[index].status = newStatus; // 'accepted' ou 'rejected'
    localStorage.setItem(STORAGE_KEYS.DOCS, JSON.stringify(docs));
    return docs[index];
  }
  return null;
}

export function clearStoredDocs() {
  localStorage.removeItem(STORAGE_KEYS.DOCS);
}

/* ==========================================================================
   GESTION DES ABSENCES (Synchronisation bidirectionnelle)
   ========================================================================== */

export function getAllAbsences() {
  initDataStore();
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEYS.ABSENCES) || '[]');
  } catch {
    return [...MOCK_ABSENCES_STUDENT_1];
  }
}

function deduplicateAbsences(absences) {
  const seen = new Set();
  return absences.filter(absence => {
    if (absence.id === undefined || absence.id === null) return true;
    const key = `${absence.studentId ?? 'unknown'}:${absence.id}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function getEffectiveAbsenceStatus(absence, documents) {
  const document = absence.id === undefined || absence.id === null
    ? null
    : documents.find(item => String(item.absenceId) === String(absence.id));
  if (document?.status === 'accepted' || absence.justificatifStatut === 'accepted') {
    return 'justified';
  }
  if (document?.status === 'rejected' || absence.justificatifStatut === 'rejected') {
    return 'unjustified';
  }
  return absence.statut === 'justified' ? 'justified' : 'unjustified';
}

export function calculateAbsenceRate(absenceCount, studentCount = 1) {
  const plannedSessions = TOTAL_PLANNED_SESSIONS * studentCount;
  return plannedSessions > 0 ? (absenceCount / plannedSessions) * 100 : 0;
}

/**
 * Calcule les statistiques depuis le stockage partagé, sans compter les justificatifs
 * comme des absences. Le taux rapporte les absences aux séances prévues pour les étudiants.
 */
export function getAbsenceStatistics(groupId = '__all_groups__') {
  const groups = getStoredGroups();
  const studentsByGroup = getStoredStudentsByGroup();
  const allAbsences = deduplicateAbsences(getAllAbsences());
  const documents = getStoredDocs();
  const studentGroupById = new Map();

  Object.entries(studentsByGroup).forEach(([storedGroupId, students]) => {
    students.forEach(student => {
      const studentId = String(student.id);
      if (!studentGroupById.has(studentId)) studentGroupById.set(studentId, storedGroupId);
    });
  });

  const groupStatistics = groups.map(group => {
    const students = studentsByGroup[group.id] || [];
    const studentIds = new Set(students.map(student => String(student.id)));
    const absences = allAbsences.filter(absence => studentIds.has(String(absence.studentId)));
    const justified = absences.filter(absence =>
      getEffectiveAbsenceStatus(absence, documents) === 'justified'
    ).length;
    const total = absences.length;
    const studentCount = studentIds.size;

    return {
      id: group.id,
      nom: group.nom,
      studentCount,
      totalAbsences: total,
      justified,
      unjustified: total - justified,
      absenceRate: calculateAbsenceRate(total, studentCount),
    };
  });

  const selectedGroups = groupId === '__all_groups__'
    ? groupStatistics
    : groupStatistics.filter(group => group.id === groupId);
  const selectedStudentIds = groupId === '__all_groups__'
    ? null
    : new Set((studentsByGroup[groupId] || []).map(student => String(student.id)));
  const selectedAbsences = groupId === '__all_groups__'
    ? allAbsences
    : allAbsences.filter(absence => selectedStudentIds.has(String(absence.studentId)));
  const selectedDocuments = groupId === '__all_groups__'
    ? documents
    : documents.filter(document => {
        const absence = allAbsences.find(item => String(item.id) === String(document.absenceId));
        return absence && selectedStudentIds.has(String(absence.studentId));
      });
  const justified = selectedAbsences.filter(absence =>
    getEffectiveAbsenceStatus(absence, documents) === 'justified'
  ).length;
  const studentIds = groupId === '__all_groups__'
    ? new Set(studentGroupById.keys())
    : selectedStudentIds;
  const studentCount = studentIds?.size || 0;
  const total = selectedAbsences.length;

  return {
    groupId,
    studentCount,
    totalAbsences: total,
    justified,
    unjustified: total - justified,
    pendingDocuments: selectedDocuments.filter(document => document.status === 'pending').length,
    absenceRate: calculateAbsenceRate(total, studentCount),
    groups: selectedGroups,
  };
}

export function saveAllAbsences(absences) {
  localStorage.setItem(STORAGE_KEYS.ABSENCES, JSON.stringify(absences));
}

/**
 * Récupère les absences d'un étudiant donné, enrichies des statuts de justificatifs.
 */
export function getStudentAbsences(studentId = 1) {
  const all = getAllAbsences();
  const docs = getStoredDocs();

  return all
    .filter(a => Number(a.studentId) === Number(studentId))
    .map(a => {
      const localDoc = docs.find(d => String(d.absenceId) === String(a.id));
      if (localDoc) {
        return {
          ...a,
          justificatif: localDoc.fileName,
          justificatifStatut: localDoc.status,
          statut: localDoc.status === 'accepted' ? 'justified' : (localDoc.status === 'rejected' ? 'unjustified' : a.statut),
        };
      }
      return a;
    });
}

/**
 * Calcule les statistiques d'un étudiant (total, justifiées, non-justifiées, taux d'absence).
 */
export function calculateStudentStats(studentId = 1) {
  const absences = getStudentAbsences(studentId);
  const total = absences.length;
  const justified = absences.filter(a => a.statut === 'justified').length;
  const unjustified = total - justified;
  const plannedSessions = TOTAL_PLANNED_SESSIONS;
  const taux = calculateAbsenceRate(total).toFixed(1);

  return {
    total,
    justified,
    unjustified,
    taux,
    plannedSessions,
  };
}

/**
 * Enregistre une nouvelle absence en vérifiant les doublons.
 */
export function createAbsence(absenceData) {
  const all = getAllAbsences();

  const isDuplicate = all.some(a =>
    Number(a.studentId) === Number(absenceData.studentId) &&
    a.date === absenceData.date &&
    a.matiere.trim().toLowerCase() === absenceData.matiere.trim().toLowerCase() &&
    a.typeSeance === absenceData.typeSeance
  );

  if (isDuplicate) {
    return {
      success: false,
      error: 'Une absence est déjà enregistrée pour cet étudiant à cette date et pour cette séance.',
    };
  }

  const newAbsence = {
    id: Date.now(),
    studentId: Number(absenceData.studentId),
    date: absenceData.date,
    matiere: absenceData.matiere,
    enseignant: absenceData.enseignant || 'Dr. Kaddour',
    seance: `${absenceData.typeSeance} ${absenceData.heureDebut}–${absenceData.heureFin}`,
    typeSeance: absenceData.typeSeance,
    heureDebut: absenceData.heureDebut,
    heureFin: absenceData.heureFin,
    statut: 'unjustified',
    justificatif: null,
    justificatifStatut: null,
  };

  all.unshift(newAbsence);
  saveAllAbsences(all);

  return { success: true, absence: newAbsence };
}

/**
 * Met à jour une absence existante (date, matière, typeSeance, horaires).
 */
export function updateAbsence(absenceId, updateFields) {
  const all = getAllAbsences();
  const index = all.findIndex(a => String(a.id) === String(absenceId));

  if (index === -1) {
    return { success: false, error: 'Absence introuvable.' };
  }

  const current = all[index];
  const updated = {
    ...current,
    date: updateFields.date || current.date,
    matiere: updateFields.matiere || current.matiere,
    typeSeance: updateFields.typeSeance || current.typeSeance,
    heureDebut: updateFields.heureDebut || current.heureDebut,
    heureFin: updateFields.heureFin || current.heureFin,
    seance: `${updateFields.typeSeance || current.typeSeance} ${updateFields.heureDebut || current.heureDebut}–${updateFields.heureFin || current.heureFin}`,
  };

  all[index] = updated;
  saveAllAbsences(all);

  return { success: true, absence: updated };
}

/**
 * Traite une décision de justificatif par l'enseignant (Accepté / Refusé).
 */
export function decideJustificatif(docId, decision) {
  const updatedDoc = updateDocStatus(docId, decision);
  if (!updatedDoc) {
    return { success: false, error: 'Justificatif introuvable.' };
  }

  const all = getAllAbsences();
  const absIndex = all.findIndex(a => String(a.id) === String(updatedDoc.absenceId));
  if (absIndex !== -1) {
    all[absIndex].justificatifStatut = decision;
    if (decision === 'accepted') {
      all[absIndex].statut = 'justified';
    } else {
      all[absIndex].statut = 'unjustified';
    }
    saveAllAbsences(all);
  }

  return { success: true, doc: updatedDoc };
}

/* ==========================================================================
   MODULE GESTION DES ÉTUDIANTS (Espace Administration)
   ========================================================================== */

/**
 * Retourne le dictionnaire des étudiants groupés par groupeId depuis le store.
 */
export function getStoredStudentsByGroup() {
  initDataStore();
  try {
    const raw = JSON.parse(localStorage.getItem(STORAGE_KEYS.STUDENTS) || '{}');
    if (Object.keys(raw).length === 0) {
      // Fallback si vide
      const initial = {};
      Object.keys(MOCK_STUDENTS_BY_GROUP).forEach(gid => {
        initial[gid] = MOCK_STUDENTS_BY_GROUP[gid].map(st => ({
          ...st,
          numeroEtudiant: `ETU-2026-${String(st.id).padStart(4, '0')}`,
          groupeId: gid,
        }));
      });
      return initial;
    }
    return raw;
  } catch {
    return MOCK_STUDENTS_BY_GROUP;
  }
}

/**
 * Sauvegarde le dictionnaire des étudiants dans localStorage.
 */
export function saveStoredStudentsByGroup(data) {
  localStorage.setItem(STORAGE_KEYS.STUDENTS, JSON.stringify(data));
}

function getStoredTeachers() {
  initDataStore();
  let teachers;
  try {
    teachers = JSON.parse(localStorage.getItem(STORAGE_KEYS.TEACHERS) || '[]');
  } catch (error) {
    throw new Error('Les données des enseignants sont illisibles dans le stockage local.', { cause: error });
  }
  if (!Array.isArray(teachers)) {
    throw new Error('Les données des enseignants doivent être une liste.');
  }
  return teachers;
}

function saveStoredTeachers(teachers) {
  localStorage.setItem(STORAGE_KEYS.TEACHERS, JSON.stringify(teachers));
}

function getStoredGroups() {
  initDataStore();
  let groups;
  try {
    groups = JSON.parse(localStorage.getItem(STORAGE_KEYS.GROUPS) || '[]');
  } catch (error) {
    throw new Error('Les données des groupes sont illisibles dans le stockage local.', { cause: error });
  }
  if (!Array.isArray(groups)) {
    throw new Error('Les données des groupes doivent être une liste.');
  }
  return groups;
}

function saveStoredGroups(groups) {
  localStorage.setItem(STORAGE_KEYS.GROUPS, JSON.stringify(groups));
}

function validateGroupFields({ nom, specialite, matiere }, requireIdentifier = false, id = '') {
  if (![nom, specialite, matiere].every(value => typeof value === 'string' && value.trim())) {
    return 'Veuillez renseigner le nom, la spécialité et la matière du groupe.';
  }
  if (requireIdentifier && !/^[a-z0-9]+(?:-[a-z0-9]+)*$/i.test(id.trim())) {
    return 'L’identifiant doit contenir uniquement des lettres, chiffres et tirets.';
  }
  return null;
}

function normalizeGroupFields({ nom, specialite, matiere }) {
  return {
    nom: nom.trim(),
    specialite: specialite.trim(),
    matiere: matiere.trim(),
  };
}

function assignStudentsToGroupInDictionary(groupsDict, targetGroup, studentIds) {
  const requestedIds = [...new Set(studentIds.map(id => String(id)))];
  if (!groupsDict[targetGroup.id]) groupsDict[targetGroup.id] = [];

  const occurrences = new Map();
  Object.entries(groupsDict).forEach(([groupId, students]) => {
    students.forEach(student => {
      const key = String(student.id);
      if (requestedIds.includes(key)) {
        occurrences.set(key, [...(occurrences.get(key) || []), { groupId, student }]);
      }
    });
  });
  const missingId = requestedIds.find(id => !occurrences.has(id));
  if (missingId) return `L’étudiant ${missingId} est introuvable.`;
  const duplicateId = requestedIds.find(id => occurrences.get(id).length !== 1);
  if (duplicateId) return `L’identifiant interne ${duplicateId} apparaît plusieurs fois dans les groupes.`;

  requestedIds.forEach(id => {
    const [{ groupId, student }] = occurrences.get(id);
    if (groupId === targetGroup.id) return;
    groupsDict[groupId] = groupsDict[groupId].filter(item => String(item.id) !== id);
    groupsDict[targetGroup.id].push({
      ...student,
      groupeId: targetGroup.id,
      filiere: targetGroup.specialite,
    });
    if (Number(id) === 1) {
      MOCK_STUDENT.groupeId = targetGroup.id;
      MOCK_STUDENT.groupeNom = targetGroup.nom;
      MOCK_STUDENT.filiere = targetGroup.specialite;
    }
  });
  return null;
}

export function getAllGroups() {
  return getStoredGroups();
}

export function getGroupOverview() {
  const groups = getStoredGroups();
  const studentsByGroup = getStoredStudentsByGroup();
  const teachers = getStoredTeachers();
  return groups.map(group => ({
    ...group,
    effectif: (studentsByGroup[group.id] || []).length,
    enseignants: teachers.filter(teacher => teacher.groupes?.includes(group.id)),
    etudiants: studentsByGroup[group.id] || [],
  }));
}

export function createGroup(groupData, studentIds = []) {
  const id = typeof groupData.id === 'string' ? groupData.id.trim().toUpperCase() : '';
  const validationError = validateGroupFields(groupData, true, id);
  if (validationError) return { success: false, error: validationError };

  const groups = getStoredGroups();
  if (groups.some(group => String(group.id).trim().toUpperCase() === id)) {
    return { success: false, error: 'Cet identifiant de groupe existe déjà.' };
  }

  const newGroup = {
    id,
    ...normalizeGroupFields(groupData),
    effectif: 0,
  };
  const groupsDict = Object.fromEntries(
    Object.entries(getStoredStudentsByGroup()).map(([groupId, students]) => [
      groupId,
      students.map(student => ({ ...student })),
    ])
  );
  const assignmentError = assignStudentsToGroupInDictionary(groupsDict, newGroup, studentIds);
  if (assignmentError) return { success: false, error: assignmentError };

  groups.push(newGroup);
  saveStoredGroups(groups);
  saveStoredStudentsByGroup(groupsDict);
  return { success: true, group: newGroup };
}

export function updateGroup(groupId, groupData, studentIds = []) {
  const validationError = validateGroupFields(groupData);
  if (validationError) return { success: false, error: validationError };

  const groups = getStoredGroups();
  const index = groups.findIndex(group => String(group.id) === String(groupId));
  if (index === -1) return { success: false, error: 'Groupe introuvable.' };

  const updatedGroup = {
    ...groups[index],
    ...normalizeGroupFields(groupData),
  };
  const groupsDict = Object.fromEntries(
    Object.entries(getStoredStudentsByGroup()).map(([storedGroupId, students]) => [
      storedGroupId,
      students.map(student => ({ ...student })),
    ])
  );
  const assignmentError = assignStudentsToGroupInDictionary(groupsDict, updatedGroup, studentIds);
  if (assignmentError) return { success: false, error: assignmentError };

  groupsDict[updatedGroup.id] = (groupsDict[updatedGroup.id] || []).map(student => ({
    ...student,
    filiere: updatedGroup.specialite,
  }));
  groups[index] = updatedGroup;
  saveStoredGroups(groups);
  saveStoredStudentsByGroup(groupsDict);
  return { success: true, group: updatedGroup };
}

function validateTeacherFields({ nom, prenom, email, identifiantEnseignant, matiere, groupes }) {
  if (![nom, prenom, email, identifiantEnseignant, matiere].every(value => typeof value === 'string' && value.trim()) ||
      !Array.isArray(groupes) || groupes.length === 0) {
    return 'Veuillez renseigner tous les champs et sélectionner au moins un groupe.';
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
    return 'Veuillez saisir une adresse e-mail professionnelle valide.';
  }
  const validGroups = new Set(getStoredGroups().map(group => group.id));
  if (groupes.some(groupId => !validGroups.has(groupId))) {
    return 'Un ou plusieurs groupes sélectionnés sont inconnus.';
  }
  return null;
}

function normalizeTeacherFields({ nom, prenom, email, identifiantEnseignant, matiere, groupes }) {
  const cleanNom = nom.trim();
  const cleanPrenom = prenom.trim();
  return {
    nom: cleanNom,
    prenom: cleanPrenom,
    name: `${cleanPrenom} ${cleanNom}`,
    email: email.trim().toLowerCase(),
    identifiantEnseignant: identifiantEnseignant.trim().toUpperCase(),
    matiere: matiere.trim(),
    groupes: [...new Set(groupes)],
    avatar: `${cleanPrenom.charAt(0)}${cleanNom.charAt(0)}`.toUpperCase(),
  };
}

export function getAllTeachers() {
  return getStoredTeachers();
}

export function createTeacher(teacherData) {
  const validationError = validateTeacherFields(teacherData);
  if (validationError) return { success: false, error: validationError };

  const teachers = getStoredTeachers();
  const normalized = normalizeTeacherFields(teacherData);
  if (teachers.some(teacher => teacher.email?.trim().toLowerCase() === normalized.email)) {
    return { success: false, error: 'Cette adresse e-mail est déjà attribuée à un enseignant.' };
  }
  if (teachers.some(teacher => teacher.identifiantEnseignant?.trim().toUpperCase() === normalized.identifiantEnseignant)) {
    return { success: false, error: 'Cet identifiant enseignant est déjà attribué.' };
  }

  const maxId = teachers.reduce((max, teacher) => Math.max(max, Number(teacher.id) || 0), 0);
  const newTeacher = {
    ...normalized,
    id: maxId + 1,
    departement: 'Informatique',
    annee: '2026-2027',
  };
  teachers.push(newTeacher);
  saveStoredTeachers(teachers);
  return { success: true, teacher: newTeacher };
}

export function updateTeacher(teacherId, teacherData) {
  const validationError = validateTeacherFields(teacherData);
  if (validationError) return { success: false, error: validationError };

  const teachers = getStoredTeachers();
  const index = teachers.findIndex(teacher => String(teacher.id) === String(teacherId));
  if (index === -1) return { success: false, error: 'Enseignant introuvable.' };

  const normalized = normalizeTeacherFields(teacherData);
  const emailConflict = teachers.some(teacher =>
    String(teacher.id) !== String(teacherId) && teacher.email?.trim().toLowerCase() === normalized.email
  );
  if (emailConflict) {
    return { success: false, error: 'Cette adresse e-mail est déjà utilisée par un autre enseignant.' };
  }
  const identifierConflict = teachers.some(teacher =>
    String(teacher.id) !== String(teacherId) &&
    teacher.identifiantEnseignant?.trim().toUpperCase() === normalized.identifiantEnseignant
  );
  if (identifierConflict) {
    return { success: false, error: 'Cet identifiant enseignant est déjà attribué à un autre enseignant.' };
  }

  const updatedTeacher = { ...teachers[index], ...normalized };
  teachers[index] = updatedTeacher;
  saveStoredTeachers(teachers);
  return { success: true, teacher: updatedTeacher };
}

/**
 * Retourne la liste plate de tous les étudiants enregistrés avec leurs statistiques d'absence.
 */
export function getAllStudentsList() {
  const groupsDict = getStoredStudentsByGroup();
  const groups = getStoredGroups();
  const list = [];

  Object.entries(groupsDict).forEach(([gid, students]) => {
    students.forEach(st => {
      const stats = calculateStudentStats(st.id);
      const groupInfo = groups.find(g => g.id === gid) || { nom: gid };
      list.push({
        ...st,
        groupeId: gid,
        groupeNom: groupInfo.nom,
        numeroEtudiant: st.numeroEtudiant || `ETU-2026-${String(st.id).padStart(4, '0')}`,
        absencesTotal: stats.total,
        absencesJustifiees: stats.justified,
        absencesNonJustifiees: stats.unjustified,
        taux: stats.taux,
      });
    });
  });

  return list;
}

/**
 * Ajoute un nouvel étudiant et l'affecte au groupe spécifié.
 * Vérifie l'unicité de l'email et du numéro étudiant.
 * @returns {{ success: boolean, error?: string, student?: object }}
 */
export function createStudent({ nom, prenom, email, numeroEtudiant, groupeId }) {
  if (!nom || !prenom || !email || !numeroEtudiant || !groupeId) {
    return { success: false, error: 'Tous les champs sont obligatoires.' };
  }

  const allStudents = getAllStudentsList();

  // Contrôle d'unicité
  const emailExists = allStudents.some(s => s.email.trim().toLowerCase() === email.trim().toLowerCase());
  if (emailExists) {
    return { success: false, error: 'Cette adresse e-mail est déjà attribuée à un étudiant.' };
  }

  const numExists = allStudents.some(s => s.numeroEtudiant.trim().toUpperCase() === numeroEtudiant.trim().toUpperCase());
  if (numExists) {
    return { success: false, error: 'Ce numéro étudiant est déjà attribué.' };
  }

  // Calcul du nouvel ID étudiant stable
  const maxId = allStudents.reduce((max, s) => Math.max(max, Number(s.id) || 0), 0);
  const newId = maxId + 1;

  // Calcul avatar
  const avatar = `${prenom.charAt(0)}${nom.charAt(0)}`.toUpperCase();

  // Déduire la filière depuis le groupe
  const groupObj = getStoredGroups().find(g => g.id === groupeId);
  if (!groupObj) return { success: false, error: 'Le groupe sélectionné est introuvable.' };
  const filiere = groupObj.specialite;

  const newStudent = {
    id: newId,
    nom: nom.trim(),
    prenom: prenom.trim(),
    name: `${prenom.trim()} ${nom.trim()}`,
    email: email.trim().toLowerCase(),
    numeroEtudiant: numeroEtudiant.trim().toUpperCase(),
    groupeId,
    filiere,
    annee: '2026-2027',
    avatar,
    absences: [],
  };

  const groupsDict = getStoredStudentsByGroup();
  if (!groupsDict[groupeId]) {
    groupsDict[groupeId] = [];
  }
  groupsDict[groupeId].push(newStudent);
  saveStoredStudentsByGroup(groupsDict);

  return { success: true, student: newStudent };
}

/**
 * Modifie les informations d'un étudiant et gère le changement de groupe sans casser ses absences.
 * @returns {{ success: boolean, error?: string, student?: object }}
 */
export function updateStudent(studentId, { nom, prenom, email, numeroEtudiant, groupeId }) {
  const groupsDict = getStoredStudentsByGroup();
  let foundStudent = null;
  let oldGroupId = null;

  // Recherche de l'étudiant dans l'ensemble des groupes
  Object.keys(groupsDict).forEach(gid => {
    const idx = groupsDict[gid].findIndex(s => Number(s.id) === Number(studentId));
    if (idx !== -1) {
      foundStudent = groupsDict[gid][idx];
      oldGroupId = gid;
    }
  });

  if (!foundStudent) {
    return { success: false, error: 'Étudiant introuvable.' };
  }

  // Vérification de collision email ou numéro étudiant avec un autre étudiant
  const allStudents = getAllStudentsList();
  const emailConflict = allStudents.some(s => Number(s.id) !== Number(studentId) && s.email.trim().toLowerCase() === email.trim().toLowerCase());
  if (emailConflict) {
    return { success: false, error: 'Cette adresse e-mail est déjà utilisée par un autre étudiant.' };
  }

  const numConflict = allStudents.some(s => Number(s.id) !== Number(studentId) && s.numeroEtudiant.trim().toUpperCase() === numeroEtudiant.trim().toUpperCase());
  if (numConflict) {
    return { success: false, error: 'Ce numéro étudiant est déjà attribué à un autre étudiant.' };
  }

  // Mise à jour des informations
  const groupObj = getStoredGroups().find(g => g.id === groupeId);
  if (!groupObj) return { success: false, error: 'Le groupe sélectionné est introuvable.' };
  const updatedStudent = {
    ...foundStudent,
    nom: nom.trim(),
    prenom: prenom.trim(),
    name: `${prenom.trim()} ${nom.trim()}`,
    email: email.trim().toLowerCase(),
    numeroEtudiant: numeroEtudiant.trim().toUpperCase(),
    groupeId,
    filiere: groupObj.specialite,
    avatar: `${prenom.charAt(0)}${nom.charAt(0)}`.toUpperCase(),
  };

  // Gestion du déplacement de groupe
  if (oldGroupId !== groupeId) {
    // Retirer de l'ancien groupe
    groupsDict[oldGroupId] = groupsDict[oldGroupId].filter(s => Number(s.id) !== Number(studentId));
    // Ajouter dans le nouveau groupe
    if (!groupsDict[groupeId]) groupsDict[groupeId] = [];
    groupsDict[groupeId].push(updatedStudent);
  } else {
    // Mise à jour sur place
    const idx = groupsDict[oldGroupId].findIndex(s => Number(s.id) === Number(studentId));
    groupsDict[oldGroupId][idx] = updatedStudent;
  }

  saveStoredStudentsByGroup(groupsDict);

  // Si l'étudiant modifié est l'étudiant connecté Démo (ID 1), mettre à jour MOCK_STUDENT
  if (Number(studentId) === 1) {
    MOCK_STUDENT.nom = updatedStudent.nom;
    MOCK_STUDENT.prenom = updatedStudent.prenom;
    MOCK_STUDENT.name = updatedStudent.name;
    MOCK_STUDENT.email = updatedStudent.email;
    MOCK_STUDENT.groupeId = updatedStudent.groupeId;
    MOCK_STUDENT.avatar = updatedStudent.avatar;
  }

  return { success: true, student: updatedStudent };
}

/* ==========================================================================
   SERVICES API SIMULÉS (Prêts à basculer vers Spring Boot)
   ========================================================================== */

export function fetchStudentDataLive() {
  return new Promise((resolve) => {
    setTimeout(() => {
      const absences = getStudentAbsences(1);
      const stats = calculateStudentStats(1);
      const storedStudent = getAllStudentsList().find(student => Number(student.id) === 1);
      resolve({
        student: storedStudent
          ? {
              ...MOCK_STUDENT,
              ...storedStudent,
              name: `${storedStudent.prenom} ${storedStudent.nom}`,
              groupeNom: getStoredGroups().find(group => group.id === storedStudent.groupeId)?.nom || storedStudent.groupeId,
            }
          : MOCK_STUDENT,
        stats: {
          total: stats.total,
          justified: stats.justified,
          unjustified: stats.unjustified,
          taux: stats.taux,
        },
        absences,
      });
    }, 150);
  });
}

export function fetchTeacherDataLive() {
  return new Promise((resolve) => {
    setTimeout(() => {
      const allAbs = getAllAbsences();
      const groupsDict = getStoredStudentsByGroup();
      const groups = getStoredGroups().map(g => ({
        ...g,
        effectif: (groupsDict[g.id] || []).length,
      }));

      const studentsByGroup = {};
      Object.keys(groupsDict).forEach(gid => {
        studentsByGroup[gid] = groupsDict[gid].map(st => {
          const stAbs = allAbs.filter(a => Number(a.studentId) === Number(st.id));
          const stats = calculateStudentStats(st.id);
          return {
            ...st,
            absences: stAbs,
            absencesTotal: stats.total,
            absencesJustifiees: stats.justified,
            absencesNonJustifiees: stats.unjustified,
            taux: stats.taux,
          };
        });
      });

      resolve({
        teacher: MOCK_TEACHER,
        groups,
        studentsByGroup,
      });
    }, 150);
  });
}

export function fetchAdminDataLive() {
  return new Promise((resolve) => {
    setTimeout(() => {
      const groupsDict = getStoredStudentsByGroup();
      const groups = getStoredGroups().map(g => ({
        ...g,
        effectif: (groupsDict[g.id] || []).length,
      }));
      resolve({
        admin: MOCK_ADMIN,
        groups,
        teacher: MOCK_TEACHER,
      });
    }, 150);
  });
}
