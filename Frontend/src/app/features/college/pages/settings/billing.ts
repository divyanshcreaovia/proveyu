import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface InvoiceItem {
  num: string;
  contractRef: string;
  amount: string;
  rawAmount: number;
  date: string;
  dueDate: string;
  status: 'Paid' | 'Pending' | 'Overdue';
  description: string;
  studentsCount: number;
  paymentMethod?: string;
  transactionRef?: string;
}

@Component({
  selector: 'app-college-billing',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './billing.html',
  styleUrl: './billing.scss',
})
export class Billing {
  activeTab: 'invoices' | 'subscription' | 'payment-methods' = 'invoices';
  toastMessage: string | null = null;
  selectedInvoice: InvoiceItem | null = null;
  isProcessingPayment: boolean = false;
  showPaySection: boolean = false;
  selectedPayMethod: 'neft' | 'card' = 'neft';

  plan = {
    name: 'ProveYu Enterprise Campus Bundle',
    tagline: 'Annual Institutional Assessment & Skill Passport Suite',
    status: 'Active',
    enrolledStudents: 350,
    testsIncludedPerStudent: 7,
    totalProctoredTests: 2450,
    ratePerStudent: 3500,
    totalContractValue: 1225000,
    amountPaid: 1050000,
    outstandingBalance: 175000,
    paidPercentage: 86,
    renewalDate: 'Sep 30, 2027',
    planType: 'Comprehensive Tier Benchmark Tier 1-3 License'
  };

  invoices: InvoiceItem[] = [
    {
      num: 'INV-2026-001',
      contractRef: 'CTR-CAMPUS-7701',
      amount: '₹3,50,000',
      rawAmount: 350000,
      date: '15 Sep 2026',
      dueDate: '30 Sep 2026',
      status: 'Paid',
      description: 'Tranche 1: Onboarding, Student Portal Provisioning & Diagnostic Benchmark Test 1',
      studentsCount: 350,
      paymentMethod: 'NEFT / RTGS Corporate Bank Transfer',
      transactionRef: 'HDFCR20260915881029'
    },
    {
      num: 'INV-2026-002',
      contractRef: 'CTR-CAMPUS-7701',
      amount: '₹3,50,000',
      rawAmount: 350000,
      date: '01 Oct 2026',
      dueDate: '15 Oct 2026',
      status: 'Paid',
      description: 'Tranche 2: Proctored Core Technical Benchmark Assessment (Tests 2 & 3)',
      studentsCount: 350,
      paymentMethod: 'Corporate Credit Card',
      transactionRef: 'TXN-998244102941'
    },
    {
      num: 'INV-2026-003',
      contractRef: 'CTR-CAMPUS-7701',
      amount: '₹3,50,000',
      rawAmount: 350000,
      date: '15 Oct 2026',
      dueDate: '31 Oct 2026',
      status: 'Paid',
      description: 'Tranche 3: Mid-Semester Coding Benchmark & Recruiter Gateway Activation',
      studentsCount: 350,
      paymentMethod: 'NEFT / RTGS Corporate Bank Transfer',
      transactionRef: 'ICICIR20261015339014'
    },
    {
      num: 'INV-2026-004',
      contractRef: 'CTR-CAMPUS-7701',
      amount: '₹1,75,000',
      rawAmount: 175000,
      date: '01 Nov 2026',
      dueDate: '15 Nov 2026',
      status: 'Pending',
      description: 'Final Milestone: Executive Placement Dossier, Skill Passports & Corporate Drive Sync',
      studentsCount: 350
    }
  ];

  constructor() {
    this.selectedInvoice = this.invoices[3];
  }

  showToast(msg: string) {
    this.toastMessage = msg;
    setTimeout(() => {
      this.toastMessage = null;
    }, 3500);
  }

  downloadInvoice(inv: InvoiceItem) {
    this.showToast(`Downloading official invoice tax PDF for ${inv.num}...`);
  }

  toggleInlinePay(inv?: InvoiceItem) {
    if (inv) {
      this.selectedInvoice = inv;
      this.showPaySection = true;
    } else {
      this.selectedInvoice = this.invoices[3];
      this.showPaySection = !this.showPaySection;
    }
  }

  closePaySection() {
    this.showPaySection = false;
  }

  processPayment() {
    this.isProcessingPayment = true;
    setTimeout(() => {
      this.isProcessingPayment = false;
      this.showPaySection = false;
      const target = this.selectedInvoice || this.invoices[3];
      target.status = 'Paid';
      target.paymentMethod = this.selectedPayMethod === 'neft' 
        ? 'RTGS / NEFT Direct Corporate Wire' 
        : 'Corporate Card / Institutional NetBanking';
      target.transactionRef = `TXN-${Date.now().toString().slice(-8)}`;

      this.plan.amountPaid += target.rawAmount;
      this.plan.outstandingBalance = Math.max(0, this.plan.totalContractValue - this.plan.amountPaid);
      this.plan.paidPercentage = Math.round((this.plan.amountPaid / this.plan.totalContractValue) * 100);

      this.showToast(`Payment of ${target.amount} for ${target.num} processed successfully!`);
    }, 1200);
  }
}
