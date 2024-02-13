function! elin#internal#job#start(command, options) abort
  return s:start(a:command, a:options)
endfunction

function! elin#internal#job#stop(job) abort
  return s:stop(a:job)
endfunction

function! elin#internal#job#redir(command, callback) abort
  let d = {'result': '', 'callback': a:callback}
  call s:start(a:command, {
        \ 'out_cb': funcref('s:on_redir_out', d),
        \ 'close_cb': funcref('s:on_redir_close', d),
        \ })
endfunction

function! s:on_redir_out(_, out) abort dict
  for out in elin#util#ensure_array(a:out)
    let self.result = self.result . out
  endfor
endfunction

function! s:on_redir_close(_) abort dict
  call self.callback(self.result)
endfunction

if has('nvim')
  function! s:on_exit(callback_key, options, job, exit_code, event_type) abort
    return a:options[a:callback_key](a:job)
  endfunction

  function! s:start(command, options) abort
    let options = elin#util#select_keys(a:options, ['cwd'])
    if has_key(a:options, 'out_cb')
      let options['on_stdout'] = {j,d,e -> a:options['out_cb'](j, d)}
    endif
    if has_key(a:options, 'err_cb')
      let options['on_stderr'] = {j,d,e -> a:options['err_cb'](j, d)}
    endif
    if has_key(a:options, 'close_cb')
      let options['on_exit'] = funcref('s:on_exit', ['close_cb', a:options])
    endif
    if has_key(a:options, 'exit_cb')
      let options['on_exit'] = funcref('s:on_exit', ['exit_cb', a:options])
    endif
    return jobstart(a:command, options)
  endfunction

  function! s:stop(job_id) abort
    return jobstop(a:job_id)
  endfunction

else
  function! s:start(command, options) abort
    let options = elin#util#select_keys(a:options, ['cwd', 'out_cb', 'err_cb', 'close_cb', 'exit_cb'])
    return job_start(a:command, options)
  endfunction

  function! s:stop(job_id) abort
    return job_stop(a:job_id)
  endfunction
endif
