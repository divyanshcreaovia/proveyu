import { Component, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { NotificationService } from '../../../../shared/services/notification.service';

@Component({
  selector: 'app-select-role',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './select-role.html',
  styleUrl: './select-role.scss',
})
export class SelectRole {
  private router = inject(Router);
  private http = inject(HttpClient);
  private cdr = inject(ChangeDetectorRef);
  private notificationService = inject(NotificationService);

  formData = {
    fullName: '',
    email: '',
    phone: '',
    password: '',
    confirmPassword: '',
    role: 'CANDIDATE' // Default role
  };

  isSubmitting = false;
  isSubmitted = false;
  errorMessage = '';
  errorField: 'email' | 'phone' | 'password' | 'confirmPassword' | 'general' | '' = '';

  toastMessage = '';
  toastType: 'success' | 'error' = 'error';
  private toastTimer: any = null;

  showToast(message: string, type: 'success' | 'error' = 'error') {
    if (this.toastTimer) {
      clearTimeout(this.toastTimer);
    }
    this.toastMessage = message;
    this.toastType = type;
    this.cdr.detectChanges();

    this.toastTimer = setTimeout(() => {
      this.toastMessage = '';
      this.cdr.detectChanges();
    }, 5000);
  }

  clearErrors() {
    if (this.errorMessage) {
      this.errorMessage = '';
      this.errorField = '';
      this.cdr.detectChanges();
    }
  }

  submitRegistration() {
    this.errorMessage = '';
    this.errorField = '';

    if (!this.formData.fullName?.trim()) {
      const msg = 'Please enter your Full Name.';
      this.errorMessage = msg;
      this.errorField = 'general';
      this.showToast(msg, 'error');
      return;
    }
    if (!this.formData.email?.trim()) {
      const msg = 'Please enter your Email Address.';
      this.errorMessage = msg;
      this.errorField = 'email';
      this.showToast(msg, 'error');
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.formData.email.trim())) {
      const msg = 'Please enter a valid email address (e.g. name@example.com).';
      this.errorMessage = msg;
      this.errorField = 'email';
      this.showToast(msg, 'error');
      return;
    }
    if (!this.formData.phone?.trim()) {
      const msg = 'Please enter your Phone Number.';
      this.errorMessage = msg;
      this.errorField = 'phone';
      this.showToast(msg, 'error');
      return;
    }
    if (!/^\+?[\d\s-]{10,}$/.test(this.formData.phone.trim())) {
      const msg = 'Please enter a valid phone number (minimum 10 digits).';
      this.errorMessage = msg;
      this.errorField = 'phone';
      this.showToast(msg, 'error');
      return;
    }
    if (!this.formData.password) {
      const msg = 'Please enter a password.';
      this.errorMessage = msg;
      this.errorField = 'password';
      this.showToast(msg, 'error');
      return;
    }
    if (this.formData.password.length < 6) {
      const msg = 'Password must be at least 6 characters long.';
      this.errorMessage = msg;
      this.errorField = 'password';
      this.showToast(msg, 'error');
      return;
    }
    if (this.formData.password !== this.formData.confirmPassword) {
      const msg = 'Passwords do not match. Please re-enter your password.';
      this.errorMessage = msg;
      this.errorField = 'confirmPassword';
      this.showToast(msg, 'error');
      return;
    }

    const payload = {
      email: this.formData.email.trim(),
      password: this.formData.password,
      fullName: this.formData.fullName.trim(),
      phone: this.formData.phone.trim(),
      role: this.formData.role
    };

    this.isSubmitting = true;
    this.isSubmitted = false;
    this.cdr.detectChanges();

    this.http.post<any>('http://localhost:8080/api/v1/auth/register', payload).subscribe({
      next: (res) => {
        this.isSubmitting = false;
        this.isSubmitted = true;
        this.showToast('Registration successful! Redirecting to login...', 'success');
        this.cdr.detectChanges();

        setTimeout(() => {
          this.router.navigate(['/login']);
        }, 2200);
      },
      error: (err) => {
        console.error('Registration API error:', err);
        this.isSubmitting = false;
        this.isSubmitted = false;

        let msg = 'Registration failed. Please check your details and try again.';
        const errorCode = err?.error?.errorCode;

        if (errorCode === 'EMAIL_EXISTS') {
          msg = 'Email is already registered! Please sign in or use a different email.';
          this.errorField = 'email';
        } else if (errorCode === 'PHONE_EXISTS') {
          msg = 'Phone number is already registered! Please use a different phone number.';
          this.errorField = 'phone';
        } else if (err?.error?.message) {
          msg = err.error.message;
          const lower = msg.toLowerCase();
          if (lower.includes('email')) {
            this.errorField = 'email';
          } else if (lower.includes('phone') || lower.includes('mobile')) {
            this.errorField = 'phone';
          }
        } else if (err?.error?.data && typeof err.error.data === 'object') {
          const fieldMsgs = Object.values(err.error.data);
          if (fieldMsgs.length > 0) {
            msg = fieldMsgs.join('. ');
          }
        }

        this.errorMessage = msg;
        this.showToast(msg, 'error');
        this.notificationService.showError(msg, 'Registration Failed');
        this.cdr.detectChanges();
      }
    });
  }
}
