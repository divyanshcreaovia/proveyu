import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private router = inject(Router);
  private http = inject(HttpClient);

  email = '';
  password = '';
  rememberMe = true;
  isLoggingIn = false;
  loginSuccessMessage = '';



  onForgotPassword(event: Event) {
    event.preventDefault();
    alert('Password reset link sent to your registered email address.');
  }

  onLogin(event?: Event) {
    if (event) {
      event.preventDefault();
    }
    this.isLoggingIn = true;

    const payload = {
      email: this.email,
      password: this.password
    };

    this.http.post('http://localhost:8080/api/v1/auth/login', payload).subscribe({
      next: (res: any) => {
        this.loginSuccessMessage = 'Login successful! Redirecting...';
        
        // Save user session details in both sessionStorage and localStorage
        if (res.data) {
          if (res.data.fullName) {
            sessionStorage.setItem('candidateFullName', res.data.fullName);
            localStorage.setItem('candidateFullName', res.data.fullName);
          }
          if (res.data.token) {
            sessionStorage.setItem('token', res.data.token);
            localStorage.setItem('token', res.data.token);
          }
          if (res.data.userId) {
            sessionStorage.setItem('userId', res.data.userId);
            localStorage.setItem('userId', res.data.userId);
          }
          if (res.data.email) {
            sessionStorage.setItem('email', res.data.email);
            localStorage.setItem('email', res.data.email);
          }
          if (res.data.role) {
            sessionStorage.setItem('role', res.data.role);
            localStorage.setItem('role', res.data.role);
          }
        } else {
          sessionStorage.setItem('candidateFullName', 'Candidate');
          localStorage.setItem('candidateFullName', 'Candidate');
        }

        setTimeout(() => {
          const targetRoute = res.data && res.data.role === 'RECRUITER' ? '/recruiter/profile' : '/candidate/dashboard';
          this.router.navigate([targetRoute]).then(() => {
            this.isLoggingIn = false;
          });
        }, 400);
      },
      error: (err) => {
        console.error('Login failed', err);
        this.isLoggingIn = false;
        alert('Login failed. Please check your credentials.');
      }
    });
  }
}
