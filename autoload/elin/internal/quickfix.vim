function! elin#internal#quickfix#getqflist() abort
  let list =  getqflist()
  return map(list, {_, v -> s:complete_filename(v)})
endfunction

function! elin#internal#quickfix#getloclist(winnr) abort
  let list =  getloclist(a:winnr)
  return map(list, {_, v -> s:complete_filename(v)})
endfunction

function! s:complete_filename(v) abort
  let res = copy(a:v)
  let nr = get(res, 'bufnr', -1)
  let res['filename'] = fnamemodify(bufname(nr), ':p')
  return res
endfunction
