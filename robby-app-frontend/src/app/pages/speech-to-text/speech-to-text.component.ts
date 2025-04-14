import { Component } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';


@Component({
  selector: 'app-speech-to-text',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './speech-to-text.component.html',
  styleUrl: './speech-to-text.component.css'
})
export class SpeechToTextComponent {
formGroup = new FormGroup({
  file: new FormControl(null),
})

onFileSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  console.log(input.files);
}
}
