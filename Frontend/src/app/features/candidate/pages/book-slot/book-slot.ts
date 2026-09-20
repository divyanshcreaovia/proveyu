import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-book-slot',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './book-slot.html',
  styleUrl: './book-slot.scss',
})
export class BookSlot {
  currentStep = 1;

  exams = [
    { id: 1, name: 'DSA', duration: '90 mins', difficulty: 'Medium', price: 999, desc: 'Data Structures and Algorithms. Focus on problem solving and logical thinking.', icon: 'bi-code-square', color: 'text-primary' },
    { id: 2, name: 'Full Stack Java', duration: '120 mins', difficulty: 'Hard', price: 1499, desc: 'End-to-end development with Java, Spring Boot and React.', icon: 'bi-window-stack', color: 'text-danger' },
    { id: 3, name: 'System Design', duration: '120 mins', difficulty: 'Hard', price: 1499, desc: 'Design scalable systems and architecture patterns.', icon: 'bi-diagram-3', color: 'text-info' },
    { id: 4, name: 'React Frontend', duration: '90 mins', difficulty: 'Medium', price: 1199, desc: 'Modern frontend development with React and TypeScript.', icon: 'bi-filetype-jsx', color: 'text-primary' },
    { id: 5, name: 'DevOps', duration: '90 mins', difficulty: 'Medium', price: 1199, desc: 'CI/CD, Docker, Kubernetes and cloud platforms.', icon: 'bi-infinity', color: 'text-success' },
    { id: 6, name: 'Database Design', duration: '90 mins', difficulty: 'Medium', price: 999, desc: 'SQL, NoSQL and database optimization.', icon: 'bi-database', color: 'text-warning' }
  ];

  selectedExam: any = null;

  selectExam(exam: any) {
    this.selectedExam = exam;
  }

  nextStep() {
    if (this.currentStep < 4) {
      this.currentStep++;
    }
  }

  prevStep() {
    if (this.currentStep > 1) {
      this.currentStep--;
    }
  }
}
