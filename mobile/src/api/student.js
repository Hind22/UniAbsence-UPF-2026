import { apiRequest, downloadProtectedFile } from './client.js';

function requireList(value, resourceName) {
  if (!Array.isArray(value)) {
    throw new Error(`La réponse du serveur pour ${resourceName} est invalide.`);
  }
  return value;
}

function formatUploadDate(value) {
  if (!value) return '';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('fr-FR');
}

function normalizeStudent(student) {
  if (!student || !Number.isInteger(student.id)) {
    throw new Error('Le profil étudiant reçu du serveur est invalide.');
  }
  const firstName = student.firstName || '';
  const lastName = student.lastName || '';
  const name = `${firstName} ${lastName}`.trim();
  const initials = `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase();

  return {
    ...student,
    name: name || student.email,
    avatar: student.avatar || initials || 'ET',
    filiere: student.program || 'Non renseignée',
    annee: student.academicYear || 'Non renseignée',
  };
}

function normalizeAbsence(absence, justifications) {
  if (!absence || !Number.isInteger(absence.id)) {
    throw new Error('Une absence reçue du serveur est invalide.');
  }
  const docs = justifications.filter(doc => Number(doc.absenceId) === absence.id);
  const latestDoc = docs[0] || null;
  const status = latestDoc?.status === 'accepted'
    ? 'justified'
    : absence.status === 'justified' ? 'justified' : 'unjustified';

  return {
    ...absence,
    date: absence.absenceDate,
    matiere: absence.subject,
    enseignant: absence.teacherName || 'Non renseigné',
    seance: absence.sessionLabel || absence.sessionType || 'Séance',
    typeSeance: absence.sessionType || 'CM',
    heureDebut: absence.startTime || '',
    heureFin: absence.endTime || '',
    statut: status,
    justificatif: latestDoc?.fileName || null,
    justificatifStatut: latestDoc?.status || null,
  };
}

export async function fetchStudentDashboardData() {
  const [profile, rawAbsences, rawJustifications, attendanceStatsResult] = await Promise.all([
    apiRequest('/api/v1/students/me'),
    apiRequest('/api/v1/absences'),
    apiRequest('/api/v1/justifications'),
    apiRequest('/api/v1/students/me/attendance-stats').catch(() => null),
  ]);
  const justifications = requireList(rawJustifications, 'les justificatifs')
    .map(doc => ({
      ...doc,
      uploadDate: formatUploadDate(doc.submittedAt),
    }));
  const absences = requireList(rawAbsences, 'les absences')
    .map(absence => normalizeAbsence(absence, justifications));
  const student = normalizeStudent(profile);
  const total = absences.length;
  const justified = absences.filter(absence => absence.statut === 'justified').length;

  const plannedSessions = attendanceStatsResult?.plannedSessionsCount ?? null;
  const distinctMissedSessions = attendanceStatsResult?.distinctMissedSessionsCount ?? null;
  const absenceRate = attendanceStatsResult?.absenceRate ?? null;
  const explanation = attendanceStatsResult?.explanation ?? null;
  const referencePeriod = attendanceStatsResult?.referencePeriodLabel ?? 'Semestre 1 (2026-2027)';

  return {
    student,
    absences,
    justifications,
    stats: {
      total,
      justified,
      unjustified: total - justified,
      absenceRate,
      plannedSessions,
      distinctMissedSessions,
      explanation,
      referencePeriod,
    },
  };
}

export async function fetchStudentTimetable() {
  const timetable = await apiRequest('/api/v1/students/me/timetable');
  if (
    !timetable
    || !Array.isArray(timetable.sessions)
    || typeof timetable.demonstration !== 'boolean'
  ) {
    throw new Error('La réponse du serveur pour l’emploi du temps est invalide.');
  }
  return timetable;
}

export function submitStudentJustification(absenceId, file) {
  const formData = new FormData();
  formData.append('absenceId', String(absenceId));
  formData.append('file', file, file.name);
  return apiRequest('/api/v1/justifications/upload', {
    method: 'POST',
    body: formData,
  });
}

export function downloadStudentJustification(id, fileName) {
  return downloadProtectedFile(`/api/v1/justifications/${id}/file`, fileName);
}
