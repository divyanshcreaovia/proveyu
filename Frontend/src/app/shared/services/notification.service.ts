import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type NotificationType = 'success' | 'error' | 'warning' | 'info';

export interface ToastItem {
  id: string;
  type: NotificationType;
  title?: string;
  message: string;
  duration?: number;
}

export interface ModalNotificationOptions {
  type: NotificationType;
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  onConfirm?: () => void;
  onCancel?: () => void;
}

@Injectable({
  providedIn: 'root',
})
export class NotificationService {
  private toastsSubject = new BehaviorSubject<ToastItem[]>([]);
  public toasts$ = this.toastsSubject.asObservable();

  private modalSubject = new BehaviorSubject<ModalNotificationOptions | null>(null);
  public modal$ = this.modalSubject.asObservable();

  showToast(type: NotificationType, message: string, title?: string, duration: number = 4000): void {
    const id = `toast-${Date.now()}-${Math.random().toString(36).substr(2, 5)}`;
    const toast: ToastItem = { id, type, message, title, duration };

    const currentToasts = this.toastsSubject.value;
    this.toastsSubject.next([...currentToasts, toast]);

    if (duration > 0) {
      setTimeout(() => {
        this.removeToast(id);
      }, duration);
    }
  }

  showSuccess(message: string, title?: string, duration: number = 4000): void {
    this.showToast('success', message, title || 'Success', duration);
  }

  showError(message: string, title?: string, duration: number = 5000): void {
    this.showToast('error', message, title || 'Action Failed', duration);
  }

  showWarning(message: string, title?: string, duration: number = 4000): void {
    this.showToast('warning', message, title || 'Notice', duration);
  }

  showInfo(message: string, title?: string, duration: number = 4000): void {
    this.showToast('info', message, title || 'Information', duration);
  }

  removeToast(id: string): void {
    const updated = this.toastsSubject.value.filter((t) => t.id !== id);
    this.toastsSubject.next(updated);
  }

  showModal(options: ModalNotificationOptions): void {
    this.modalSubject.next(options);
  }

  closeModal(): void {
    this.modalSubject.next(null);
  }
}
