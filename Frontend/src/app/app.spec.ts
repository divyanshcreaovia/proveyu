import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('creates the app with global toasts and confirm dialog', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(fixture.componentInstance).toBeTruthy();
    expect(el.querySelector('pm-toasts')).not.toBeNull();
    expect(el.querySelector('pm-confirm-dialog')).not.toBeNull();
  });

  it('treats portal URLs as portal routes', () => {
    const app = TestBed.createComponent(App).componentInstance as unknown as { portal(u: string): boolean };
    expect(app.portal('/college/dashboard')).toBe(true);
    expect(app.portal('/company/drives')).toBe(true);
    expect(app.portal('/student/dashboard')).toBe(true);
    expect(app.portal('/admin/colleges')).toBe(true);
    expect(app.portal('/how-it-works')).toBe(false);
  });
});
