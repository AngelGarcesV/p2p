export interface Servidor {
  servidorId: string;
  host: string;
  puerto: number;
  estado: string;
  intentosReconexion: number;
  ultimaConexion: string | null;
}
