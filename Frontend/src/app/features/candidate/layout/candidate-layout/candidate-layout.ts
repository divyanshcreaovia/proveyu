import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Sidebar } from '../sidebar/sidebar';

@Component({
  selector: 'app-candidate-layout',
  standalone: true,
  imports: [RouterOutlet, Sidebar],
  templateUrl: './candidate-layout.html',
  styleUrl: './candidate-layout.scss',
})
export class CandidateLayout {}
