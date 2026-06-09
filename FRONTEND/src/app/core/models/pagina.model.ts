export interface Pagina<T> {
  datos: T[];
  total: number;
  pagina: number;
  tamanoPagina: number;
}
