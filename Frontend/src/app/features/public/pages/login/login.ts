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
  selectedRole = 'candidate';
  rememberMe = true;
  isLoggingIn = false;
  loginSuccessMessage = '';

  selectRoleTab(roleGroup: 'candidate' | 'recruiter') {
    if (roleGroup === 'candidate') {
      this.selectedRole = 'candidate';
    } else {
      this.selectedRole = 'recruiter_agency';
    }
  }

  isRecruiterSelected(): boolean {
    return this.selectedRole.startsWith('recruiter');
  }

  quickLogin(type: 'candidate' | 'agency' | 'startup' | 'enterprise') {
    if (type === 'candidate') {
      this.selectedRole = 'candidate';
      this.email = 'rahul.sharma@example.com';
      this.password = 'candidate123';
    } else if (type === 'agency') {
      this.selectedRole = 'recruiter_agency';
      this.email = 'apex.placements@example.com';
      this.password = 'agency123';
    } else if (type === 'startup') {
      this.selectedRole = 'recruiter_startup';
      this.email = 'hiring@cloudscale.io';
      this.password = 'startup123';
    } else if (type === 'enterprise') {
      this.selectedRole = 'recruiter_company';
      this.email = 'talent@fintechcorp.com';
      this.password = 'enterprise123';
    }

    // Auto-trigger login and routing on quick persona click
    this.onLogin();
  }

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
        
        // Save candidate full name and token
        if (res.data) {
          if (res.data.fullName) {
            localStorage.setItem('candidateFullName', res.data.fullName);
          }
          if (res.data.token) {
            localStorage.setItem('token', res.data.token);
          }
        } else {
          localStorage.setItem('candidateFullName', 'Candidate');
        }

        setTimeout(() => {
          const targetRoute = this.selectedRole === 'candidate' ? '/candidate/dashboard' : '/candidate/dashboard';
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
