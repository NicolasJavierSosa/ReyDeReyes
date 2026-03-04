package com.example.demo.controlador;

import com.example.demo.dto.ClienteDto;
import com.example.demo.dto.ClienteEstadoRequest;
import com.example.demo.dto.ClienteFormLinkDto;
import com.example.demo.dto.ClienteFormLinkRequest;
import com.example.demo.dto.ClienteRequest;
import com.example.demo.servicio.ClienteService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/clientes")
public class ClienteApiController {

	private final ClienteService clienteService;
	private final String formToken;

	public ClienteApiController(
		ClienteService clienteService,
		@Value("${app.integrations.googleForms.token:}") String formToken
	) {
		this.clienteService = clienteService;
		this.formToken = formToken == null ? "" : formToken.trim();
	}

	@GetMapping
	public List<ClienteDto> listar(@RequestParam(defaultValue = "all") String status) {
		return clienteService.listar(status);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ClienteDto crear(@Valid @RequestBody ClienteRequest request) {
		return clienteService.crear(request);
	}

	@PutMapping("/{id}")
	public ClienteDto actualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest request) {
		return clienteService.actualizar(id, request);
	}

	@PatchMapping("/{id}/estado")
	public ClienteDto cambiarEstado(@PathVariable Long id, @Valid @RequestBody ClienteEstadoRequest request) {
		return clienteService.cambiarEstado(id, request.active());
	}

	@GetMapping("/form-link")
	public ClienteFormLinkDto obtenerFormLink() {
		return new ClienteFormLinkDto(clienteService.obtenerLinkFormulario());
	}

	@PutMapping("/form-link")
	public ClienteFormLinkDto guardarFormLink(@RequestBody ClienteFormLinkRequest request) {
		return new ClienteFormLinkDto(clienteService.guardarLinkFormulario(request.link()));
	}

	@PostMapping("/form")
	@ResponseStatus(HttpStatus.CREATED)
	public ClienteDto registrarDesdeFormulario(
		@Valid @RequestBody ClienteRequest request,
		@RequestHeader(value = "X-Form-Token", required = false) String token
	) {
		if (!formToken.isEmpty() && (token == null || !formToken.equals(token))) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token invalido");
		}
		return clienteService.registrarDesdeFormulario(request);
	}
}
