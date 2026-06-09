export interface Archivo {
  id: string;
  remitente: string;
  ipRemitente: string;
  nombreArchivo: string;
  extension: string;
  rutaArchivo: string;
  hashSha256: string;
  tamano: number;
  fechaRecepcion: string;
  servidorOrigen: string;
  destinatario: string;
}
