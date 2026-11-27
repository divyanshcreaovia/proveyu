import { routes } from '../../../app.routes';

/** The shared reset page takes its "Back to sign in" target from route data, so every route that uses it must give one. */
describe('forgot-password routes', () => {
  for (const portal of ['college', 'student', 'company', 'cxo']) {
    it(`${portal} points back to its own sign-in page`, () => {
      const route = routes.find(r => r.path === `${portal}/forgot-password`);
      expect(route?.data?.['loginUrl']).toBe(`/${portal}/login`);
      expect(route?.data?.['variant']).toBe(portal);
    });
  }
});
