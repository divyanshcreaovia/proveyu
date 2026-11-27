import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { CollegeRegister } from './college-register';

/** Step 2 of college registration: the placement officer's e-mail must be on the college domain (the backend's rule). */
describe('CollegeRegister: TPO e-mail and college domain', () => {
  let page: CollegeRegister;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    page = TestBed.runInInjectionContext(() => new CollegeRegister());
    page.form.controls.college.patchValue({
      collegeName: 'Sample College', aicteCode: 'S-1', university: 'Sample University', city: 'Pune', state: 'Maharashtra', emailDomain: 'college.edu.in',
    });
    page.next();
    expect(page.step()).toBe(1);
  });

  const officer = (tpoEmail: string) => page.form.controls.tpo.patchValue({ tpoName: 'Sample Officer', tpoPhone: '9999999999', tpoEmail });

  it('stops at step 2 when the e-mail is on another domain', () => {
    officer('tpo@other.com');
    page.next();
    expect(page.form.errors?.['domainMismatch']).toBe(true);
    expect(page.domainError()).toBe(true);
    expect(page.step()).toBe(1);
  });

  it('does not accept a domain that only ends with the same letters', () => {
    officer('tpo@fakecollege.edu.in');
    page.next();
    expect(page.step()).toBe(1);
  });

  for (const email of ['tpo@college.edu.in', 'tpo@cs.college.edu.in', 'TPO@College.EDU.in']) {
    it(`continues for ${email}`, () => {
      officer(email);
      page.next();
      expect(page.form.errors).toBeNull();
      expect(page.step()).toBe(2);
    });
  }

  it('checks again when the college domain is changed afterwards', () => {
    officer('tpo@college.edu.in');
    page.form.controls.college.patchValue({ emailDomain: 'newcollege.ac.in' });
    page.next();
    expect(page.step()).toBe(1);
  });

  it('sends nothing to the server while moving between steps', () => {
    officer('tpo@college.edu.in');
    page.next();
    TestBed.inject(HttpTestingController).verify();
  });
});
