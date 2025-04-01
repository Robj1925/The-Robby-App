import { provideRouter, RouterLink, RouterOutlet, Routes } from '@angular/router';
import { HomeComponent } from './pages/home/home.component';
import { AboutComponent } from './pages/about/about.component';
import { SandboxComponent } from './pages/sandbox/sandbox.component';

export const routes: Routes = [
    { path: '', component: HomeComponent },
    { path: 'about', component: AboutComponent },
    { path: 'sandbox', component: SandboxComponent },
];
