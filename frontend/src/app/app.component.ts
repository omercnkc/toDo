import { Component } from '@angular/core';

import { PageTitleService } from './core/services/page-title.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss'],
})
export class AppComponent {
  constructor(pageTitle: PageTitleService) {
    pageTitle.start();
  }
}
