/**
 * ⚠️ DONNÉES FICTIVES – Emploi du temps L3 Informatique 2026-2027
 * Endpoint futur : GET /api/v1/students/me/timetable
 */

export const DAYS = [
  { key: 'lundi',    label: 'Lun', full: 'Lundi' },
  { key: 'mardi',    label: 'Mar', full: 'Mardi' },
  { key: 'mercredi', label: 'Mer', full: 'Mercredi' },
  { key: 'jeudi',    label: 'Jeu', full: 'Jeudi' },
  { key: 'vendredi', label: 'Ven', full: 'Vendredi' },
  { key: 'samedi',   label: 'Sam', full: 'Samedi' },
];

/** Retourne le jour courant (lundi par défaut si dimanche). */
export function getCurrentDayKey() {
  const map = ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi'];
  const today = map[new Date().getDay()];
  return DAYS.find(d => d.key === today) ? today : 'lundi';
}

export const TIMETABLE = {
  lundi: [
    { debut: '08:00', fin: '10:00', matiere: 'Algorithmique avancée',   enseignant: 'Dr. Kaddour', salle: 'Amphi A',  type: 'CM' },
    { debut: '10:00', fin: '12:00', matiere: 'Génie logiciel',           enseignant: 'Dr. Meziane', salle: 'Salle 8',  type: 'TD' },
    { debut: '14:00', fin: '16:00', matiere: 'Bases de données',         enseignant: 'M. Rahmani',  salle: 'Salle 12', type: 'TD' },
  ],
  mardi: [
    { debut: '08:00', fin: '10:00', matiere: 'Réseaux informatiques',    enseignant: 'Mme. Haddad', salle: 'Amphi B',  type: 'CM' },
    { debut: '14:00', fin: '16:00', matiere: "Systèmes d'exploitation",  enseignant: 'M. Bouzidi',  salle: 'Labo 3',   type: 'TP' },
    { debut: '16:00', fin: '18:00', matiere: 'Algorithmique avancée',    enseignant: 'Dr. Kaddour', salle: 'Labo 1',   type: 'TP' },
  ],
  mercredi: [
    { debut: '08:00', fin: '10:00', matiere: 'Génie logiciel',           enseignant: 'Dr. Meziane', salle: 'Amphi A',  type: 'CM' },
    { debut: '10:00', fin: '12:00', matiere: 'Bases de données',         enseignant: 'M. Rahmani',  salle: 'Amphi B',  type: 'CM' },
  ],
  jeudi: [
    { debut: '08:00', fin: '10:00', matiere: 'Bases de données',         enseignant: 'M. Rahmani',  salle: 'Labo 2',   type: 'TP' },
    { debut: '10:00', fin: '12:00', matiere: "Systèmes d'exploitation",  enseignant: 'M. Bouzidi',  salle: 'Amphi B',  type: 'CM' },
    { debut: '14:00', fin: '16:00', matiere: 'Réseaux informatiques',    enseignant: 'Mme. Haddad', salle: 'Salle 10', type: 'TD' },
  ],
  vendredi: [
    { debut: '10:00', fin: '12:00', matiere: 'Réseaux informatiques',    enseignant: 'Mme. Haddad', salle: 'Labo 2',   type: 'TP' },
    { debut: '14:00', fin: '16:00', matiere: 'Génie logiciel',           enseignant: 'Dr. Meziane', salle: 'Labo 1',   type: 'TP' },
  ],
  samedi: [
    { debut: '08:00', fin: '10:00', matiere: 'Algorithmique avancée',    enseignant: 'Dr. Kaddour', salle: 'Salle 5',  type: 'TD' },
    { debut: '10:00', fin: '12:00', matiere: "Systèmes d'exploitation",  enseignant: 'M. Bouzidi',  salle: 'Salle 6',  type: 'TD' },
  ],
};
