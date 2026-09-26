import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { NotificationService, ToastItem, ModalNotificationOptions } from '../../services/notification.service';

@Component({
  selector: 'app-global-notification',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './global-notification.html',
  styleUrl: './global-notification.scss',
})
export class GlobalNotification implements OnInit, OnDestroy {
  toasts: ToastItem[] = [];
  modalOptions: ModalNotificationOptions | null = null;

  private toastSub!: Subscription;
  private modalSub!: Subscription;

  constructor(private notificationService: NotificationService) {}

  ngOnInit(): void {
    this.toastSub = this.notificationService.toasts$.subscribe((list) => {
      this.toasts = list;
    });

    this.modalSub = this.notificationService.modal$.subscribe((options) => {
      this.modalOptions = options;
    });
  }

  ngOnDestroy(): void {
    if (this.toastSub) this.toastSub.unsubscribe();
    if (this.modalSub) this.modalSub.unsubscribe();
  }

  dismissToast(id: string): void {
    this.notificationService.removeToast(id);
  }

  handleConfirm(): void {
    if (this.modalOptions?.onConfirm) {
      this.modalOptions.onConfirm();
    }
    this.notificationService.closeModal();
  }

  handleCancel(): void {
    if (this.modalOptions?.onCancel) {
      this.modalOptions.onCancel();
    }
    this.notificationService.closeModal();
  }

  getIconClass(type: string): string {
    switch (type) {
      case 'success':
        return 'bi-check-circle-fill';
      case 'error':
        return 'bi-x-circle-fill';
      case 'warning':
        return 'bi-exclamation-triangle-fill';
      case 'info':
      default:
        return 'bi-info-circle-fill';
    }
  }
}
