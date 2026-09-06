package app.openstory.catalog.repository

import app.openstory.catalog.engine.matching.CatalogMatchCandidate

data class CatalogMatchSnapshot(val candidates: List<CatalogMatchCandidate>)
