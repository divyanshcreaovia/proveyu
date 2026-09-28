import { Component, inject, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login implements OnDestroy {
  private router = inject(Router);
  private http = inject(HttpClient);
  private loginSub?: Subscription;
  private timeoutHandle?: ReturnType<typeof setTimeout>;

  email = '';
  password = '';
  rememberMe = true;
  isLoggingIn = false;
  loginSuccessMessage = '';
  loginErrorMessage = '';

  ngOnDestroy() {
    this.loginSub?.unsubscribe();
    clearTimeout(this.timeoutHandle);
  }

  onForgotPassword(event: Event) {
    event.preventDefault();
    alert('Password reset link sent to your registered email address.');
  }

  private resetLoader(message?: string) {
    clearTimeout(this.timeoutHandle);
    this.isLoggingIn = false;
    if (message) {
      this.loginErrorMessage = message;
    }
  }

  onLogin(event?: Event) {
    if (event) {
      event.preventDefault();
    }

    // Prevent double-click
    if (this.isLoggingIn) return;

    this.loginErrorMessage = '';
    this.loginSuccessMessage = '';
    this.isLoggingIn = true;

    // Safety net: always reset loader after 10 seconds no matter what
    this.timeoutHandle = setTimeout(() => {
      if (this.isLoggingIn) {
        this.resetLoader('Server is taking too long. Please try again.');
      }
    }, 10000);

    const payload = {
      email: this.email,
      password: this.password
    };

    this.loginSub = this.http.post<any>('http://localhost:8080/api/v1/auth/login', payload)
      .subscribe({
        next: (res) => {
          clearTimeout(this.timeoutHandle);

          if (!res || res.success === false) {
            // Backend returned 2xx but with success: false
            this.resetLoader(res?.message || 'Login failed. Please try again.');
            return;
          }

          this.loginSuccessMessage = 'Login successful! Redirecting...';

          if (res.data) {
            if (res.data.fullName) {
              sessionStorage.setItem('candidateFullName', res.data.fullName);
            }
            if (res.data.token) {
              sessionStorage.setItem('token', res.data.token);
            }
          } else {
            sessionStorage.setItem('candidateFullName', 'Candidate');
          }

          setTimeout(() => {
            const targetRoute = res.data?.role === 'RECRUITER' ? '/recruiter/profile' : '/candidate/dashboard';
            this.router.navigate([targetRoute]).then(() => {
              this.isLoggingIn = false;
            });
          }, 400);
        },
        error: (err) => {
          const status = err?.status;
          let message = 'Login failed. Please try again.';

          if (status === 400) {
            // Validation error - email/password missing or invalid format
            const data = err?.error?.data;
            if (data?.email || data?.password) {
              message = Object.values(data).join(' ');
            } else {
              message = err?.error?.message || 'Invalid request. Please fill in all fields.';
            }
          } else if (status === 401 || status === 403) {
            message = 'Invalid email or password. Please try again.';
          } else if (status === 0) {
            message = 'Cannot connect to server. Please check your connection.';
          }

          this.resetLoader(message);
        }
      });
  }
}
