let s:buf_name = 'elin_info_buffer'
let s:title = ';; Elin Info Buffer'
let s:delimiter = ';; ----------'

function! elin#internal#buffer#info#open(...) abort
  if elin#internal#buffer#is_visible(s:buf_name)
    return
  endif

  let option = get(a:, 1, {})
  call elin#internal#buffer#open(s:buf_name, option)
endfunction

function! elin#internal#buffer#info#close() abort
  call elin#internal#buffer#close(s:buf_name)
endfunction

function! elin#internal#buffer#info#toggle(...) abort
  if elin#internal#buffer#is_visible(s:buf_name)
    return elin#internal#buffer#info#close()
  endif
  return elin#internal#buffer#info#open(get(a:, 1, {}))
endfunction

function! elin#internal#buffer#info#ready() abort
  if bufnr(s:buf_name) != -1
    return
  endif
  silent execute printf(':split %s', s:buf_name)
  silent execute ':q'

  call setbufvar(s:buf_name, 'lsp_diagnostics_enabled', 0)
  call setbufvar(s:buf_name, '&bufhidden', 'hide')
  call setbufvar(s:buf_name, '&buflisted', 0)
  call setbufvar(s:buf_name, '&buftype', 'nofile')
  call setbufvar(s:buf_name, '&filetype', 'clojure')
  call setbufvar(s:buf_name, '&swapfile', 0)
  call setbufvar(s:buf_name, '&wrap', 0)

  call elin#internal#buffer#append(s:buf_name, s:title)
  call deletebufline(s:buf_name, 1)
endfunction

function! elin#internal#buffer#info#append(s) abort
  " let s = iced#util#delete_color_code(a:s)
  " let timer = iced#system#get('timer')

  call elin#internal#buffer#append(s:buf_name, a:s, {'scroll_to_bottom': v:true})

  " call timer.start_lazily(
  "      \ 'delete_old_lines',
  "      \ g:iced#buffer#stdout#deleting_line_delay,
  "      \ funcref('s:delete_old_lines'),
  "      \ )

  if a:s !=# s:delimiter
    call elin#util#start_lazily('append_delimiter', 500, {-> elin#internal#buffer#info#append(s:delimiter)})
  endif
  " if g:iced#buffer#stdout#enable_delimiter
  "      \ && a:s !=# g:iced#buffer#stdout#delimiter_line
  "   call timer.start_lazily(
  "        \ 'append_delimiter',
  "        \ g:iced#buffer#stdout#delimiter_delay,
  "        \ {-> (g:iced#buffer#stdout#enable_delimiter)
  "        \     ? iced#buffer#stdout#append(g:iced#buffer#stdout#delimiter_line)
  "        \     : ''},
  "        \ )
  " endif

  " if ! iced#buffer#stdout#is_visible()
  "      \ && g:iced#buffer#stdout#enable_notify
  "   silent call iced#system#get('notify').notify(s, {'title': 'Stdout'})
  " endif
endfunction

function! elin#internal#buffer#info#clear() abort
  call elin#internal#buffer#clear(s:buf_name)
  call elin#internal#buffer#append(s:buf_name, s:title)
  call deletebufline(s:buf_name, 1)
endfunction

" import { Diced } from "../../types.ts";
" import * as vimBuf from "../../std/vim/buffer.ts";
"
" export const bufName = "diced_info";
"
" export async function open(diced: Diced): Promise<boolean> {
"   const denops = diced.denops;
"   const currentWin = await dpsFns.winnr(denops);
"   if (await vimBuf.isVisible(diced, bufName)) return false;
"   if (!await vimBuf.open(diced, bufName)) return false;
"   await vimBuf.focusByWinNr(diced, currentWin);
"   return true;
" }
"
" export async function ready(diced: Diced): Promise<void> {
"   const denops = diced.denops;
"   if (await dpsFns.bufnr(denops, bufName) !== -1) return;
"
"   dpsHelper.execute(
"     denops,
"     `
"     silent execute ':split ${bufName}'
"     silent execute ':q'
"
"     call setbufvar('${bufName}', 'lsp_diagnostics_enabled', 0)
"     call setbufvar('${bufName}', '&bufhidden', 'hide')
"     call setbufvar('${bufName}', '&buflisted', 0)
"     call setbufvar('${bufName}', '&buftype', 'nofile')
"     call setbufvar('${bufName}', '&filetype', 'clojure')
"     call setbufvar('${bufName}', '&swapfile', 0)
"     call setbufvar('${bufName}', '&wrap', 0)
"     `,
"   );
"
"   return;
" }
"
