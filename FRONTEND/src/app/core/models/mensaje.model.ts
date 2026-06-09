export interface Mensaje {
  id: string;
  autor: string;
  ipRemitente: string;
  contenido: string;
  hashSha256: string;
  fechaEnvio: string;
  servidorOrigen: string;
  destinatario: string;
}
