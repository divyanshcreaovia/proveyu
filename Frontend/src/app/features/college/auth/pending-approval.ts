import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { CollegeService } from '../../../services/college.service';
import { TokenStorageService } from '../../../services/token-storage.service';
import { Me } from '../../../core/models/college.models';
import { AuthShell } from './auth-shell';

@Component({
  selector: 'pm-pending-approval',
  imports: [AuthShell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pm-auth-shell [heading]="me()?.approvalStatus === 'REJECTED' ? 'Registration not approved' : 'Awaiting approval'">
      @if (me(); as m) {
        @if (m.approvalStatus === 'REJECTED') {
          <div class="alert alert-danger">Your registration for <strong>{{ m.collegeName }}</strong> was not approved.
            @if (m.approvalNote) { <br>Reason: {{ m.approvalNote }} }</div>
          <p class="small text-body-secondary">Contact support&#64;proveyu.com to appeal.</p>
        } @else {
          <div class="text-center mb-3"><i class="bi bi-hourglass-split text-warning" style="font-size:3rem"></i></div>
          <p><strong>{{ m.collegeName }}</strong> is being reviewed by the ProveYu team. You'll get an e-mail as soon as it is approved
            (usually within one working day).</p>
        }
      } @else { <div class="text-center py-3"><span class="spinner-border"></span></div> }
      <div class="d-flex gap-2 mt-3">
        <button class="btn btn-outline-primary flex-grow-1" (click)="check()"><i class="bi bi-arrow-clockwise"></i> Check again</button>
        <button class="btn btn-outline-secondary" (click)="auth.logout('/college/login')">Sign out</button>
      </div>
    </pm-auth-shell>`,
})
export class PendingApproval implements OnInit {
  auth = inject(AuthService);
  private college = inject(CollegeService);
  private storage = inject(TokenStorageService);
  private router = inject(Router);
  me = signal<Me | null>(null);

  ngOnInit(): void { this.check(); }

  check(): void {
    this.college.loadMe().subscribe(m => {
      this.me.set(m);
      this.storage.update('collegeApprovalStatus', m.approvalStatus);
      if (m.approvalStatus === 'APPROVED') this.router.navigateByUrl('/college/dashboard');
    });
  }
}
