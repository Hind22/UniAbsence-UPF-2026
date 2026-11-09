import { ApiError, apiRequest, downloadProtectedFile } from './client.js';

function requireList(value, resourceName) {
  if (!Array.isArray(value)) {
    throw new Error(`La réponse du serveur pour ${resourceName} est invalide.`);
  }
  return value;
}

function escapeName(firstName, lastName) {
  return `${firstName || ''} ${lastName || ''}`.trim();
}

function initials(firstName, lastName) {
  return `${firstName?.charAt(0) || ''}${lastName?.charAt(0) || ''}`.toUpperCase() || 'UA';
}

function formatDate(value) {
  if (!value) return '';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('fr-FR');
}

function hasStoredFile(filePath) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(pdf|jpg|png)$/i
    .test(String(filePath || ''));
}

function normalizeTeacher(teacher) {
  if (!teacher || !Number.isInteger(teacher.id)) {
    throw new Error('Le profil enseignant reçu du serveur est invalide.');
  }
  return {
    ...teacher,
    name: escapeName(teacher.firstName, teacher.lastName) || teacher.email,
    avatar: teacher.avatar || initials(teacher.firstName, teacher.lastName),
    matiere: teacher.subject || 'Non renseignée',
    departement: teacher.department || 'Non renseigné',
    grade: teacher.grade || 'Enseignant',
  };
}

function normalizeStudent(student, absences, justifications, stats = null) {
  const studentAbsences = absences
    .filter(absence => Number(absence.studentId) === student.id)
    .map(absence => {
      const doc = justifications.find(item => Number(item.absenceId) === absence.id);
      return {
        ...absence,
        date: absence.absenceDate,
        matiere: absence.subject,
        enseignant: absence.teacherName || '',
        seance: absence.sessionLabel || absence.sessionType || '',
        typeSeance: absence.sessionType || '',
        heureDebut: absence.startTime || '',
        heureFin: absence.endTime || '',
        statut: doc?.status === 'accepted' || absence.status === 'justified'
          ? 'justified'
          : 'unjustified',
        justificatif: doc?.fileName || null,
        justificatifStatut: doc?.status || null,
      };
    });
  const justifiedCount = studentAbsences.filter(absence => absence.statut === 'justified').length;
  const pendingDocsCount = justifications.filter(doc =>
    Number(doc.studentId) === student.id && doc.status === 'pending'
  ).length;

  return {
    ...student,
    nom: student.lastName,
    prenom: student.firstName,
    filiere: student.program || 'Non renseignée',
    avatar: student.avatar || initials(student.firstName, student.lastName),
    absences: studentAbsences,
    absencesTotal: studentAbsences.length,
    absencesJustifiees: justifiedCount,
    absencesNonJustifiees: studentAbsences.length - justifiedCount,
    pendingDocsCount,
    taux: stats?.absenceRate ?? null,
    plannedSessions: stats?.plannedSessionsCount ?? null,
    distinctMissedSessions: stats?.distinctMissedSessionsCount ?? null,
    statsJustifiedAbsences: stats?.justifiedAbsencesCount ?? justifiedCount,
    statsUnjustifiedAbsences: stats?.unjustifiedAbsencesCount ?? studentAbsences.length - justifiedCount,
    explanation: stats?.explanation ?? null,
    referencePeriod: stats?.referencePeriodLabel ?? 'Semestre 1 (2026-2027)',
  };
}

function normalizeJustification(doc, absencesById) {
  const absence = absencesById.get(Number(doc.absenceId));
  return {
    ...doc,
    matiere: absence?.subject || 'Matière non renseignée',
    absenceDate: formatDate(absence?.absenceDate),
    uploadDate: formatDate(doc.submittedAt),
  };
}

export async function fetchTeacherData() {
  let rawTeacher;
  try {
    rawTeacher = await apiRequest('/api/v1/teachers/me');
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) {
      throw new ApiError(
        'Ce compte enseignant n’est pas encore associé à un profil enseignant. Contactez l’administration.',
        403
      );
    }
    throw error;
  }

  const [rawGroups, rawStudents, rawAbsences, rawJustifications] = await Promise.all([
    apiRequest('/api/v1/teachers/me/groups'),
    apiRequest('/api/v1/students'),
    apiRequest('/api/v1/absences'),
    apiRequest('/api/v1/justifications'),
  ]);
  const teacher = normalizeTeacher(rawTeacher);
  const groups = requireList(rawGroups, 'les groupes').map(group => ({
    ...group,
    nom: group.name,
    specialite: group.specialty || 'Spécialité non renseignée',
    matiere: group.mainSubject || teacher.matiere,
    effectif: group.studentCount,
  }));
  const absences = requireList(rawAbsences, 'les absences');
  const justifications = requireList(rawJustifications, 'les justificatifs');
  const studentList = requireList(rawStudents, 'les étudiants');
  const statsList = await Promise.all(
    studentList.map(s => apiRequest(`/api/v1/students/${s.id}/attendance-stats`))
  );
  const statsMap = new Map(statsList.map(s => [s.studentId, s]));
  const students = studentList
    .map(student => normalizeStudent(student, absences, justifications, statsMap.get(student.id)));
  const absencesById = new Map(absences.map(absence => [absence.id, absence]));
  const studentsByGroup = Object.fromEntries(groups.map(group => [
    group.id,
    students.filter(student => student.groupId === group.id),
  ]));

  return {
    teacher,
    groups,
    studentsByGroup,
    absences: absences.map(absence => ({
      ...absence,
      date: absence.absenceDate,
      matiere: absence.subject,
      enseignant: absence.teacherName || teacher.name,
      seance: absence.sessionLabel || absence.sessionType || '',
      typeSeance: absence.sessionType || '',
      heureDebut: absence.startTime || '',
      heureFin: absence.endTime || '',
      statut: absence.status,
    })),
    justifications: justifications.map(doc =>
      normalizeJustification(doc, absencesById)
    ),
  };
}

export function createTeacherAbsence(data) {
  return apiRequest('/api/v1/absences', {
    method: 'POST',
    body: JSON.stringify({
      studentId: Number(data.studentId),
      absenceDate: data.date,
      subject: data.subject,
      sessionLabel: `${data.sessionType} ${data.startTime}–${data.endTime}`,
      sessionType: data.sessionType,
      startTime: data.startTime,
      endTime: data.endTime,
    }),
  });
}

export function updateTeacherAbsence(absenceId, data) {
  return apiRequest(`/api/v1/absences/${absenceId}`, {
    method: 'PUT',
    body: JSON.stringify({
      studentId: Number(data.studentId),
      absenceDate: data.date,
      subject: data.subject,
      sessionLabel: `${data.sessionType} ${data.startTime}–${data.endTime}`,
      sessionType: data.sessionType,
      startTime: data.startTime,
      endTime: data.endTime,
      status: 'unjustified',
    }),
  });
}

export function deleteTeacherAbsence(absenceId) {
  return apiRequest(`/api/v1/absences/${absenceId}`, { method: 'DELETE' });
}

export function decideTeacherJustification(id, status) {
  return apiRequest(`/api/v1/justifications/${id}/decision`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  });
}

export function downloadTeacherJustification(id, fileName) {
  return downloadProtectedFile(`/api/v1/justifications/${id}/file`, fileName);
}

export function justificationHasFile(filePath) {
  return hasStoredFile(filePath);
}
