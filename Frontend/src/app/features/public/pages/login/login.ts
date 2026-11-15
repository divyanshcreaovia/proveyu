import { Component, inject, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../../../services/auth.service';
import { AppError } from '../../../../core/api';
import { ToastService } from '../../../../services/toast.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private router = inject(Router);
  private auth = inject(AuthService);
  private toast = inject(ToastService);
  returnUrl = input<string>();

  email = '';
  password = '';
  rememberMe = true;
  isLoggingIn = false;
  loginSuccessMessage = '';

  onForgotPassword(event: Event) {
    event.preventDefault();
    this.router.navigate(['/college/forgot-password']);
  }

  /** Signs in and redirects by role: college staff -> /college, candidate -> /candidate, recruiter -> /recruiter, admin -> /admin. */
  onLogin(event?: Event) {
    event?.preventDefault();
    this.isLoggingIn = true;
    this.auth.login(this.email.trim(), this.password, this.rememberMe).subscribe({
      next: s => {
        this.loginSuccessMessage = 'Login successful! Redirecting...';
        const home = AuthService.homeFor(s.role, s.collegeApprovalStatus);
        const ret = this.returnUrl();
        const target = ret && ret.startsWith('/') && ret.split('/')[1] === home.split('/')[1] ? ret : home;
        this.router.navigateByUrl(target).then(() => (this.isLoggingIn = false));
      },
      error: (e: AppError) => {
        this.isLoggingIn = false;
        if (e.code === 'EMAIL_NOT_VERIFIED') this.toast.warning('Please verify your e-mail first (check your inbox).');
      },
    });
  }
}
