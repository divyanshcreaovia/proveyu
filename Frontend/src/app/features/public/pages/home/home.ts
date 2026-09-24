import { Component, OnInit, OnDestroy, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-home',
  imports: [CommonModule, RouterModule],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home implements OnInit, OnDestroy {
  private cdr = inject(ChangeDetectorRef);

  activeSlide = 0;
  private intervalId: any;

  activeTestimonialTab: 'students' | 'recruiters' = 'students';
  activeFaq: number | null = 0;

  placedStudents = [
    {
      id: 1,
      name: 'Priya Sundaram',
      role: 'QA Automation',
      company: 'TechCorp',
      package: '₹9.2 LPA',
      score: '96/100',
      college: 'Tier-3 Engineering College, Salem',
      avatar: 'https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&q=80&w=200',
      rating: 5,
      badge: 'Gold Passport',
      review: 'Coming from a tier-3 college, my resume was auto-filtered. ProveYu gave me an equal stage. I scored 96% in QA at the TCS iON lab and got hired by TechCorp in 5 days!'
    },
    {
      id: 2,
      name: 'Vikramaditya Roy',
      role: 'Java Backend',
      company: 'CloudScale',
      package: '₹11.5 LPA',
      score: '94/100',
      college: 'Govt Inst of Tech, Kolkata',
      avatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&q=80&w=200',
      rating: 5,
      badge: 'Gold Passport',
      review: 'Invigilated exam centre proof gave recruiters 100% confidence. I bypassed online preliminary tests completely and went straight to the CTO round.'
    },
    {
      id: 3,
      name: 'Ananya Deshmukh',
      role: 'Full Stack Eng',
      company: 'InnovateX',
      package: '₹14.0 LPA',
      score: '98/100',
      college: 'State Tech Univ, Pune',
      avatar: 'https://images.unsplash.com/photo-1580489944761-15a19d654956?auto=format&fit=crop&q=80&w=200',
      rating: 5,
      badge: 'Top 1% Rank',
      review: 'The Universal Fresher Competency test mapped my skills to Backend & Full Stack. Received 4 direct interview invites within 7 days of my exam!'
    },
    {
      id: 4,
      name: 'Rohan Verma',
      role: 'SDET Engineer',
      company: 'Fintech Nexus',
      package: '₹10.8 LPA',
      score: '92/100',
      college: 'B.Tech IT, Bhopal',
      avatar: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&q=80&w=200',
      rating: 5,
      badge: 'Silver Passport',
      review: 'Biometric verification at the JEE lab center and multi-dimensional skill proof made hiring managers trust my capability immediately.'
    }
  ];

  recruiterTestimonials = [
    {
      id: 1,
      name: 'Rajesh Malhotra',
      role: 'VP of Engineering',
      company: 'Apex Digital',
      avatar: 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?auto=format&fit=crop&q=80&w=200',
      rating: 5,
      review: 'We reduced our fresher hiring cycle from 4 weeks to 3 days. Invigilated exam lab proof eliminates fake resumes and proxy-test takers completely.'
    },
    {
      id: 2,
      name: 'Meera Nambiar',
      role: 'Head of Talent',
      company: 'ScaleX Solutions',
      avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&q=80&w=200',
      rating: 5,
      review: 'The multi-dimensional skill breakdown is outstanding. We get exact percentages in logic, QA, and backend before conducting final interviews.'
    },
    {
      id: 3,
      name: 'Sunil Kulkarni',
      role: 'Engineering Director',
      company: 'Fintech Corp',
      avatar: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&q=80&w=200',
      rating: 5,
      review: 'Physical invigilation completely removes remote cheating risk. ProveYu Skill Passports are our go-to standard for junior developer hiring.'
    },
    {
      id: 4,
      name: 'Kavita Iyer',
      role: 'Lead Campus Recruiter',
      company: 'TechCorp India',
      avatar: 'https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&q=80&w=200',
      rating: 5,
      review: 'We hired 15 Tier-2 and Tier-3 college freshers directly through ProveYu. Every hire was production-ready from day one.'
    }
  ];

  faqs = [
    {
      q: 'How does ProveYu prevent cheating during skill verification tests?',
      a: 'We partner with established high-stakes examination infrastructure (such as TCS iON and JEE test centres) equipped with biometric fingerprint scans, physical invigilators, CCTV logging, and air-gapped exam terminals. This completely eliminates remote cheating, proxy test takers, and AI relay assistance.'
    },
    {
      q: 'What is the Universal Fresher Competency Passport?',
      a: 'Freshers are evaluated through a single comprehensive test assessing core programming logic, scenario QA aptitude, and hands-on build track choices. The resulting Skill Passport highlights multi-dimensional role matches (e.g. Primary: QA Engineer, Secondary: Java Backend), maximizing candidate placement across multiple recruiter openings.'
    },
    {
      q: 'Do candidates pay to get verified?',
      a: 'Initial registration and slot booking at partner centres is free or offered at a nominal booking fee during pilot batches. Recruiters pay a placement commission or subscription fee to access verified candidate passports.'
    },
    {
      q: 'How do recruiters skip the online assessment phase?',
      a: 'Because candidates have already completed an in-person, invigilated practical coding and QA test, recruiters can trust the verified Skill Passport scores. Verified candidates move directly to final technical and culture interview rounds.'
    }
  ];

  isHeroCardFlipped = false;
  private heroFlipIntervalId: any;

  ngOnInit() {
    this.intervalId = setInterval(() => {
      this.nextSlide();
      this.cdr.markForCheck();
    }, 5000);

    this.heroFlipIntervalId = setInterval(() => {
      this.isHeroCardFlipped = !this.isHeroCardFlipped;
      this.cdr.markForCheck();
      this.cdr.detectChanges();
    }, 5000);
  }

  ngOnDestroy() {
    if (this.intervalId) {
      clearInterval(this.intervalId);
    }
    if (this.heroFlipIntervalId) {
      clearInterval(this.heroFlipIntervalId);
    }
  }

  toggleHeroFlip() {
    this.isHeroCardFlipped = !this.isHeroCardFlipped;
    this.cdr.markForCheck();
  }


  nextSlide() {
    this.activeSlide = (this.activeSlide + 1) % 2;
  }

  prevSlide() {
    this.activeSlide = (this.activeSlide - 1 + 2) % 2;
  }

  setSlide(index: number) {
    this.activeSlide = index;
  }

  toggleFaq(index: number) {
    this.activeFaq = this.activeFaq === index ? null : index;
  }
}
