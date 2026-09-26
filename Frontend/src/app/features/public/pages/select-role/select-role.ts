import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';

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

  formData = {
    fullName: '',
    email: '',
    phone: '',
    password: '',
    confirmPassword: '',
    role: 'CANDIDATE' // Default role
  };

  isSubmitted = false;

  submitRegistration() {
    if (!this.formData.fullName || !this.formData.email || !this.formData.phone || !this.formData.password || !this.formData.role) {
        alert('Please fill in all required fields.');
        return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.formData.email)) {
        alert('Please enter a valid email address.');
        return;
    }
    if (!/^\+?[\d\s-]{10,}$/.test(this.formData.phone)) {
        alert('Please enter a valid phone number (min 10 digits).');
        return;
    }
    if (this.formData.password.length < 6) {
        alert('Password must be at least 6 characters long.');
        return;
    }
    if (this.formData.password !== this.formData.confirmPassword) {
        alert('Passwords do not match');
        return;
    }

    const payload = {
        email: this.formData.email,
        password: this.formData.password,
        fullName: this.formData.fullName,
        phone: this.formData.phone,
        role: this.formData.role
    };

    this.isSubmitted = true;
    this.http.post('http://localhost:8080/api/v1/auth/register', payload).subscribe({
      next: (res) => {
        setTimeout(() => {
          this.router.navigate(['/login']);
        }, 2200);
      },
      error: (err) => {
        console.error('Registration failed', err);
        this.isSubmitted = false;
        alert('Registration failed. Please try again.');
      }
    });
  }
}
