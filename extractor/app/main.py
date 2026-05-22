import logging
import uuid
from collections.abc import Awaitable, Callable

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.routes.extraction_routes import router as extraction_router
from app.api.routes.health_routes import router as health_router
from app.config import settings
from app.domain.exceptions.extraction_exceptions import (
    ExtractionException,
    UploadValidationException,
)
from app.logging_config import configure_logging

configure_logging(settings.log_level)
logger = logging.getLogger(__name__)

app = FastAPI(
    title=settings.app_name,
    description="API para extrair cifras em formato ChordPro a partir de PDFs, imagens e TXT.",
    version=settings.app_version,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_allowed_origins,
    allow_credentials=True,
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)


@app.middleware("http")
async def request_id_middleware(
    request: Request,
    call_next: Callable[[Request], Awaitable],
):
    request_id = request.headers.get("X-Request-ID", str(uuid.uuid4()))
    request.state.request_id = request_id

    response = await call_next(request)
    response.headers["X-Request-ID"] = request_id
    return response


@app.exception_handler(UploadValidationException)
async def upload_validation_exception_handler(
    request: Request,
    exception: UploadValidationException,
):
    return JSONResponse(
        status_code=400,
        content={
            "requestId": request.state.request_id,
            "code": exception.code,
            "message": exception.message,
            "details": exception.details,
        },
    )


@app.exception_handler(ExtractionException)
async def extraction_exception_handler(request: Request, exception: ExtractionException):
    return JSONResponse(
        status_code=422,
        content={
            "requestId": request.state.request_id,
            "code": exception.code,
            "message": exception.message,
            "details": exception.details,
        },
    )


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exception: RequestValidationError):
    return JSONResponse(
        status_code=422,
        content={
            "requestId": request.state.request_id,
            "code": "VALIDATION_ERROR",
            "message": "Requisição inválida.",
            "details": exception.errors(),
        },
    )


@app.exception_handler(Exception)
async def generic_exception_handler(request: Request, exception: Exception):
    logger.exception(
        "Unhandled API error",
        extra={"request_id": request.state.request_id},
    )
    return JSONResponse(
        status_code=500,
        content={
            "requestId": request.state.request_id,
            "code": "INTERNAL_ERROR",
            "message": "Erro interno ao processar a requisição.",
            "details": None,
        },
    )


app.include_router(health_router, tags=["Health"])
app.include_router(
    extraction_router,
    prefix="/api/v1/extractions",
    tags=["Extractions"],
)
