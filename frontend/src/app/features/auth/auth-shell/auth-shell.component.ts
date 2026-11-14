import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/** Login/register sayfalarının ortak çerçevesi: ekranın ortasında, mobilde tam genişlik kart. */
@Component({
  selector: 'app-auth-shell',
  templateUrl: './auth-shell.component.html',
  styleUrls: ['./auth-shell.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthShellComponent {
  @Input() heading = '';
  @Input() subheading = '';
}
