/**
 * All photos of the public website (except the home hero), served from public/images. To change a picture, put your file
 * there and write its name here. Landscape pictures (4:3, about 1200 px wide) fit best; others are cropped from the middle.
 *
 * web-college, web-code, web-cxo and web-students are smaller 4:3 copies of college.webp, code.webp, cxo.webp and
 * students2.webp. The rest are placeholder photos downloaded from Unsplash (free licence, https://unsplash.com/license):
 *   web-studenthero.webp  photo-1571260899304-425eee4c7efc
 *   web-company.webp  photo-1521791136064-7986c2920216
 *   web-eligibility.webp  photo-1524178232363-1fb2b075b655
 *   web-calendar.webp  photo-1506784983877-45594efa4cbe
 *   web-reports.webp  photo-1460925895917-afdab827c52f
 *   web-interview.webp  photo-1565688534245-05d6b5be184a
 *   web-import.webp  photo-1516321318423-f06f85e504b3
 *   web-offers.webp  photo-1600880292203-757bb62b4baf
 *   web-announce.webp  photo-1577563908411-5077b6dc7624
 *   web-team.webp  photo-1556761175-5973dc0f32e7
 *   web-ask.webp  photo-1573496359142-b8d87734a5a2
 *   web-boardroom.webp  photo-1450101499163-c8848c66ca85
 *   web-forecast.webp  photo-1531482615713-2afd69097998
 */
export const PUBLIC_IMAGES = {
  drive: 'images/web-college.webp', // placement cell / drives: team planning (from your college.webp)
  student: 'images/web-students.webp', // students together with laptops (from your students2.webp)
  studentHero: 'images/web-studenthero.webp', // top of the For students page
  company: 'images/web-company.webp', // company Drive Room: handshake
  eligibility: 'images/web-eligibility.webp', // a full class
  calendar: 'images/web-calendar.webp', // interview schedule: planner
  reports: 'images/web-reports.webp', // reports: charts on a laptop
  interview: 'images/web-interview.webp', // interview across a table
  test: 'images/web-code.webp', // mock tests and coding (from your code.webp)
  import: 'images/web-import.webp', // importing students: working on laptops
  offers: 'images/web-offers.webp', // offers: celebration
  announce: 'images/web-announce.webp', // announcements and chat
  team: 'images/web-team.webp', // team and roles
  leadership: 'images/web-cxo.webp', // leadership (CXO) dashboards (from your cxo.webp)
  ask: 'images/web-ask.webp', // leadership page: asking a question
  boardroom: 'images/web-boardroom.webp', // leadership page: board report
  forecast: 'images/web-forecast.webp', // leadership page: forecast
  howItWorks: 'images/howitworks.svg', // top of the How it works page
};

export type SceneKind = keyof typeof PUBLIC_IMAGES;
