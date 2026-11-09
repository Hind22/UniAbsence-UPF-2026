import { apiRequest, ApiError } from './client.js';
import { AppState } from '../router.js';

const CURRENT_ACADEMIC_YEAR = (() => {
  const now = new Date();
  const year = now.getFullYear();
  const startYear = now.getMonth() >= 8 ? year : year - 1;
  return `${startYear}-${startYear + 1}`;
})();

export function requireAdmin() {
  if (AppState.currentUser?.role !== 'administrator') {
    throw new ApiError('Accès réservé à l’administration.', 403);
  }
}

export function createAdminAccount(profileType, profileId, initialPassword) {
  requireAdmin();
  return apiRequest('/api/v1/users', {
    method: 'POST',
    body: JSON.stringify({ profileType, profileId, initialPassword }),
  });
}

export function updateAdminAccount(userId, account) {
  requireAdmin();
  return apiRequest(`/api/v1/users/${userId}`, {
    method: 'PUT',
    body: JSON.stringify(account),
  });
}

function requireList(value, label) {
  if (!Array.isArray(value)) {
    throw new ApiError(`La réponse du serveur pour ${label} est invalide.`);
  }
  return value;
}

function normalizeStudent(student, groups, absences, justifications, plannedSessions = []) {
  const group = groups.find(item => item.id === student.groupId);
  const studentAbsences = absences.filter(absence =>
    Number(absence.studentId) === Number(student.id)
  );
  const justifiedIds = new Set(justifications
    .filter(document => document.status === 'accepted')
    .map(document => Number(document.absenceId)));
  const justified = studentAbsences.filter(absence =>
    absence.status === 'justified' || justifiedIds.has(Number(absence.id))
  ).length;

  const groupPlanned = plannedSessions.filter(ps => ps.groupId === student.groupId && ps.status !== 'cancelled');
  const distinctPlannedCount = new Set(groupPlanned.map(ps => `${ps.sessionDate}#${ps.startTime}#${ps.sessionType}#${(ps.subject || '').trim().toLowerCase()}`)).size;
  const distinctMissedCount = new Set(studentAbsences.map(a => `${a.absenceDate}#${a.startTime || ''}#${a.sessionType || ''}#${(a.subject || '').trim().toLowerCase()}`)).size;
  const taux = distinctPlannedCount > 0
    ? ((distinctMissedCount / distinctPlannedCount) * 100).toFixed(1)
    : null;

  return {
    ...student,
    nom: student.lastName,
    prenom: student.firstName,
    numeroEtudiant: student.studentNumber,
    groupeId: student.groupId,
    groupeNom: group?.name || student.groupId || 'Non affecté',
    filiere: student.program || group?.specialty || 'Non renseignée',
    avatar: student.avatar || `${student.firstName?.[0] || ''}${student.lastName?.[0] || ''}`.toUpperCase(),
    absencesTotal: studentAbsences.length,
    absencesJustifiees: justified,
    absencesNonJustifiees: studentAbsences.length - justified,
    plannedSessions: distinctPlannedCount,
    distinctMissedSessions: distinctMissedCount,
    taux,
  };
}

function normalizeTeacher(teacher, groups) {
  const groupIds = Array.isArray(teacher.groupIds) ? teacher.groupIds : [];
  return {
    ...teacher,
    nom: teacher.lastName,
    prenom: teacher.firstName,
    identifiantEnseignant: teacher.teacherIdentifier,
    matiere: teacher.subject || 'Non renseignée',
    departement: teacher.department || 'Non renseigné',
    groupes: groupIds,
    avatar: teacher.avatar || `${teacher.firstName?.[0] || ''}${teacher.lastName?.[0] || ''}`.toUpperCase(),
    groupLabels: groupIds.map(id => groups.find(group => group.id === id)?.name || id),
  };
}

function normalizeGroup(group, students, teachers) {
  const groupStudents = students.filter(student => student.groupId === group.id);
  const groupTeachers = teachers.filter(teacher => teacher.groupIds?.includes(group.id));
  return {
    ...group,
    nom: group.name,
    specialite: group.specialty || 'Non renseignée',
    matiere: group.mainSubject || 'Non renseignée',
    effectif: groupStudents.length,
    etudiants: groupStudents,
    enseignants: groupTeachers,
  };
}

export async function fetchAdminData() {
  requireAdmin();
  const [rawStudents, rawTeachers, rawGroups, absences, justifications, rawPlannedSessions] = await Promise.all([
    apiRequest('/api/v1/students'),
    apiRequest('/api/v1/teachers'),
    apiRequest('/api/v1/groups'),
    apiRequest('/api/v1/absences'),
    apiRequest('/api/v1/justifications'),
    apiRequest('/api/v1/planned-sessions').catch(() => []),
  ]);
  const groups = requireList(rawGroups, 'les groupes');
  const rawAbsencesList = requireList(absences, 'les absences');
  const documents = requireList(justifications, 'les justificatifs');
  const plannedSessions = Array.isArray(rawPlannedSessions) ? rawPlannedSessions : [];
  const teachers = requireList(rawTeachers, 'les enseignants')
    .map(teacher => normalizeTeacher(teacher, groups));
  const students = requireList(rawStudents, 'les étudiants')
    .map(student => normalizeStudent(student, groups, rawAbsencesList, documents, plannedSessions));

  return {
    admin: AppState.currentUser,
    students,
    teachers,
    groups: groups.map(group => normalizeGroup(group, students, teachers)),
    absences: rawAbsencesList,
    justifications: documents,
    plannedSessions,
  };
}

function studentRequest(student, groups, currentStudent = null) {
  return {
    ...(currentStudent?.userId ? { userId: currentStudent.userId } : {}),
    studentNumber: student.numeroEtudiant.trim(),
    firstName: student.prenom.trim(),
    lastName: student.nom.trim(),
    email: student.email.trim().toLowerCase(),
    groupId: student.groupeId,
    program: groups.find(group => group.id === student.groupeId)?.specialty || null,
    academicYear: currentStudent?.academicYear || CURRENT_ACADEMIC_YEAR,
  };
}

export async function saveAdminStudent(studentId, student, groups, currentStudent = null) {
  requireAdmin();
  const body = studentRequest(student, groups, studentId ? currentStudent : null);
  const result = studentId
    ? await apiRequest(`/api/v1/students/${studentId}`, { method: 'PUT', body: JSON.stringify(body) })
    : await apiRequest('/api/v1/students', { method: 'POST', body: JSON.stringify(body) });
  return normalizeStudent(result, groups, [], []);
}

export function deleteAdminStudent(studentId) {
  requireAdmin();
  return apiRequest(`/api/v1/students/${studentId}`, { method: 'DELETE' });
}

function teacherRequest(teacher, currentTeacher = null) {
  return {
    ...(currentTeacher?.userId ? { userId: currentTeacher.userId } : {}),
    teacherIdentifier: teacher.identifiantEnseignant.trim(),
    firstName: teacher.prenom.trim(),
    lastName: teacher.nom.trim(),
    email: teacher.email.trim().toLowerCase(),
    subject: teacher.matiere.trim(),
    groupIds: [...new Set(teacher.groupes)],
    academicYear: currentTeacher?.academicYear || CURRENT_ACADEMIC_YEAR,
  };
}

export function saveAdminTeacher(teacherId, teacher, currentTeacher = null) {
  requireAdmin();
  const body = teacherRequest(teacher, currentTeacher);
  return apiRequest(
    teacherId ? `/api/v1/teachers/${teacherId}` : '/api/v1/teachers',
    { method: teacherId ? 'PUT' : 'POST', body: JSON.stringify(body) }
  );
}

export function deleteAdminTeacher(teacherId) {
  requireAdmin();
  return apiRequest(`/api/v1/teachers/${teacherId}`, { method: 'DELETE' });
}

function groupRequest(group, currentGroup = null) {
  return {
    id: group.id.trim().toUpperCase(),
    name: group.nom.trim(),
    specialty: group.specialite.trim(),
    mainSubject: group.matiere.trim(),
    academicYear: currentGroup?.academicYear || CURRENT_ACADEMIC_YEAR,
  };
}

export async function saveAdminGroup(groupId, group, studentIds = [], allStudents = [], currentGroup = null) {
  requireAdmin();
  const body = groupRequest(group, currentGroup);
  const result = groupId
    ? await apiRequest(`/api/v1/groups/${encodeURIComponent(groupId)}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    })
    : await apiRequest('/api/v1/groups', { method: 'POST', body: JSON.stringify(body) });

  try {
    for (const id of studentIds) {
      const student = allStudents.find(item => String(item.id) === String(id));
      if (!student) continue;
      await apiRequest(`/api/v1/students/${student.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          ...(student.userId ? { userId: student.userId } : {}),
          studentNumber: student.numeroEtudiant,
          firstName: student.prenom,
          lastName: student.nom,
          email: student.email,
          groupId: result.id,
          program: group.specialite.trim(),
          academicYear: student.academicYear || CURRENT_ACADEMIC_YEAR,
        }),
      });
    }
  } catch (error) {
    throw new ApiError(
      `Le groupe a été enregistré, mais une affectation d’étudiant a échoué : ${error.message}`,
      error.status || 0
    );
  }
  return result;
}

export function deleteAdminGroup(groupId) {
  requireAdmin();
  return apiRequest(`/api/v1/groups/${encodeURIComponent(groupId)}`, { method: 'DELETE' });
}

export function calculateAdminStatistics(data, selectedGroupId = '__all_groups__') {
  const plannedSessions = data.plannedSessions || [];
  const groups = data.groups.map(group => {
    const studentIds = new Set(group.etudiants.map(student => Number(student.id)));
    const absences = uniqueAbsences(data.absences.filter(absence =>
      studentIds.has(Number(absence.studentId))
    ));
    const docsByAbsence = new Map(data.justifications.map(document => [
      Number(document.absenceId),
      document,
    ]));
    const justified = absences.filter(absence =>
      absence.status === 'justified' || docsByAbsence.get(Number(absence.id))?.status === 'accepted'
    ).length;

    const groupPlanned = plannedSessions.filter(ps => ps.groupId === group.id && ps.status !== 'cancelled');
    const distinctPlannedCount = new Set(groupPlanned.map(ps => `${ps.sessionDate}#${ps.startTime}#${ps.sessionType}#${(ps.subject || '').trim().toLowerCase()}`)).size;
    let groupAbsenceRate = null;
    if (distinctPlannedCount > 0 && studentIds.size > 0) {
      const distinctMissed = new Set(absences.map(a => `${a.studentId}#${a.absenceDate}#${a.startTime || ''}#${a.sessionType || ''}#${(a.subject || '').trim().toLowerCase()}`)).size;
      groupAbsenceRate = (distinctMissed / (distinctPlannedCount * studentIds.size)) * 100;
    }

    return {
      id: group.id,
      nom: group.nom,
      studentCount: studentIds.size,
      totalAbsences: absences.length,
      justified,
      unjustified: absences.length - justified,
      plannedSessions: distinctPlannedCount,
      absenceRate: groupAbsenceRate,
    };
  });
  const scopedGroups = selectedGroupId === '__all_groups__'
    ? groups
    : groups.filter(group => group.id === selectedGroupId);
  const scopedGroupIds = new Set(scopedGroups.map(group => group.id));
  const scopedStudentIds = new Set(data.students
    .filter(student => selectedGroupId === '__all_groups__' || scopedGroupIds.has(student.groupeId))
    .map(student => Number(student.id)));
  const absences = uniqueAbsences(data.absences.filter(absence =>
    scopedStudentIds.has(Number(absence.studentId))
  ));
  const docsByAbsence = new Map(data.justifications.map(document => [
    Number(document.absenceId),
    document,
  ]));
  const justified = absences.filter(absence =>
    absence.status === 'justified' || docsByAbsence.get(Number(absence.id))?.status === 'accepted'
  ).length;

  const validGroupRates = scopedGroups.filter(g => g.absenceRate !== null);
  const globalRate = validGroupRates.length > 0
    ? validGroupRates.reduce((sum, g) => sum + g.absenceRate, 0) / validGroupRates.length
    : null;

  return {
    studentCount: scopedStudentIds.size,
    totalAbsences: absences.length,
    justified,
    unjustified: absences.length - justified,
    pendingDocuments: data.justifications.filter(document =>
      document.status === 'pending' && scopedStudentIds.has(Number(document.studentId))
    ).length,
    absenceRate: globalRate,
    groups: scopedGroups,
  };
}

function uniqueAbsences(absences) {
  const seen = new Set();
  return absences.filter(absence => {
    if (absence.id == null) return true;
    const id = Number(absence.id);
    if (seen.has(id)) return false;
    seen.add(id);
    return true;
  });
}
